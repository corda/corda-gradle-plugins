package net.corda.plugins.cpk

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION
import java.io.IOException
import java.io.InputStream
import java.nio.file.Paths
import java.security.CodeSigner
import java.security.cert.Certificate
import java.util.Base64
import java.util.jar.JarEntry
import java.util.jar.JarFile
import javax.inject.Inject

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
open class CPKDependenciesTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    private companion object {
        private const val EOF = -1

        /**
         * Additionally accepting *.EC as it's valid for [JarVerifier][java.util.jar.JarVerifier].
         * Temporally treating `META-INF/INDEX.LIST` as unsignable entry because
         * [JarVerifier][java.util.jar.JarVerifier] doesn't load its signers.
         *
         * @see [Jar](https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File)
         * @see [JarSigner](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/jarsigner.html)
         */
        private val UNSIGNED = "^META-INF/(?:(?:.+\\.(?:SF|DSA|RSA|EC)|SIG-.+)|INDEX\\.LIST)\$".toRegex()

        @Throws(IOException::class)
        private fun consume(input: InputStream, buffer: ByteArray) {
            while (input.read(buffer) != EOF) {
                continue
            }
        }

        private val JarEntry.isSignable: Boolean get() {
            return !isDirectory && !UNSIGNED.matches(name)
        }

        private fun signerCertificate(codeSigner: CodeSigner): Certificate? {
            return codeSigner.signerCertPath.certificates.firstOrNull()
        }
    }

    init {
        description = "Records this CorDapp's CPK dependencies."
        group = CORDAPP_TASK_GROUP
    }

    @get:Input
    val hashAlgorithm: Property<String> = objects.property(String::class.java)

    private val _cpks: ConfigurableFileCollection = objects.fileCollection()
    val cpks: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _cpks

    @get:Internal
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @get:OutputFile
    val cpkOutput: Provider<RegularFile> = outputDir.file(CPK_DEPENDENCIES)

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [CPKDependenciesTask] by accident.
     */
    internal fun setCPKsFrom(task: TaskProvider<DependencyCalculator>) {
        _cpks.setFrom(task.flatMap(DependencyCalculator::cordapps))
        _cpks.disallowChanges()
        dependsOn(task)
    }

    @TaskAction
    fun generate() {
        val digest = digestFor(hashAlgorithm.get().toUpperCase())

        try {
            val xmlDocument = createXmlDocument()
            val cpkDependencies = xmlDocument.createRootElement(CPK_XML_NAMESPACE, "cpkDependencies")
            val encoder = Base64.getEncoder()

            cpks.forEach { cpk ->
                logger.info("CorDapp CPK dependency: {}", cpk.name)
                JarFile(cpk).use { jar ->
                    val mainAttributes = jar.manifest.mainAttributes
                    val cpkDependency = cpkDependencies.appendElement("cpkDependency")
                    cpkDependency.appendElement("name", mainAttributes.getValue(BUNDLE_SYMBOLICNAME))
                    cpkDependency.appendElement("version", mainAttributes.getValue(BUNDLE_VERSION))

                    val signerCertificates = signerCertificatesFor(jar)
                    if (signerCertificates.size != 1) {
                        logger.error("CPK {} signed by {} sets of signers:{}",
                            cpk.name, signerCertificates.size,
                            signerCertificates.joinToString(
                                separator = System.lineSeparator(),
                                prefix = System.lineSeparator(),
                                transform = ::formatCertificates
                            )
                        )
                        throw InvalidUserDataException("CPK ${cpk.name} must be signed by exactly one set of signers")
                    }
                    val signers = cpkDependency.appendElement("signers")
                    signerCertificates.single().sortedWith(CompareCertificates()).forEach { certificate ->
                        val signingKeyHash = digest.digest(certificate.publicKey.encoded)
                        signers.appendElement("signer", encoder.encodeToString(signingKeyHash))
                            .setAttribute("algorithm", digest.algorithm)
                    }
                }
            }

            // Write CPK dependency information as XML document.
            cpkOutput.get().asFile.bufferedWriter().use(xmlDocument::writeTo)
        } catch (e: Exception) {
            throw (e as? RuntimeException) ?: InvalidUserDataException(e.message ?: "", e)
        }
    }

    private fun formatCertificates(certificates: Set<Certificate>): String {
        return certificates.joinToString(
            separator = System.lineSeparator(),
            prefix = "CERTIFICATE SET (size=${certificates.size}):${System.lineSeparator()}"
        )
    }

    @Throws(IOException::class)
    private fun signerCertificatesFor(jar: JarFile): Set<Set<Certificate>> {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        return jar.entries().asSequence().mapNotNullTo(HashSet()) { entry ->
            consume(jar.getInputStream(entry), buffer)
            val certificates = entry.codeSigners?.mapNotNullTo(HashSet(), ::signerCertificate)
            if (certificates.isNullOrEmpty()) {
                if (entry.isSignable) {
                    logger.warn("{}:{} is unsigned", Paths.get(jar.name).fileName, entry.name)
                    emptySet<Certificate>()
                } else {
                    null
                }
            } else {
                certificates
            }
        }
    }

    private class CompareCertificates : Comparator<Certificate> {
        // This can be replaced by
        //    Arrays.compare(cert1.encoded, cert2.encoded)
        // when Gradle drops support for Java 8.
        override fun compare(cert1: Certificate, cert2: Certificate): Int {
            val encoded1 = cert1.encoded
            val encoded2 = cert2.encoded
            return when {
                encoded1 === encoded2 -> 0
                else -> when (val lengthDiff = encoded1.size - encoded2.size) {
                    0 -> compare(encoded1, encoded2)
                    else -> lengthDiff
                }
            }
        }

        private fun compare(b1: ByteArray, b2: ByteArray): Int {
            b1.forEachIndexed { index, value ->
                val cmp = value.toInt() - b2[index].toInt()
                if (cmp != 0) {
                    return cmp
                }
            }
            return 0
        }
    }
}
