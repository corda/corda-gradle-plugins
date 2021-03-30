package net.corda.plugins.cpk

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
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
import java.security.cert.Certificate
import java.util.Base64
import java.util.jar.JarEntry
import java.util.jar.JarFile
import javax.inject.Inject
import kotlin.collections.HashSet

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
open class CPKDependenciesTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    private companion object {
        private const val CPK_DEPENDENCIES = "META-INF/CPKDependencies"
        private const val HASH_ALGORITHM = "SHA-256"
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
            @Suppress("ControlFlowWithEmptyBody")
            while (input.read(buffer) != EOF) {}
        }

        private val JarEntry.isSignable: Boolean get() {
            return !isDirectory && !UNSIGNED.matches(name)
        }
    }

    init {
        description = "Records this CorDapp's CPK dependencies."
        group = GROUP_NAME
    }

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
        val digest = digestFor(HASH_ALGORITHM)

        try {
            val xmlDocument = createXmlDocument()
            val cpkDependencies = xmlDocument.createRootElement(XML_NAMESPACE, "cpkDependencies")
            val encoder = Base64.getEncoder()

            cpks.forEach { cpk ->
                logger.info("CorDapp CPK dependency: {}", cpk.name)
                JarFile(cpk).use { jar ->
                    val mainAttributes = jar.manifest.mainAttributes
                    val cpkDependency = cpkDependencies.appendElement("cpkDependency")
                    cpkDependency.appendElement("name", mainAttributes.getValue(BUNDLE_SYMBOLICNAME))
                    cpkDependency.appendElement("version", mainAttributes.getValue(BUNDLE_VERSION))

                    val certificates = certificatesFor(jar)
                    if (certificates.size != 1) {
                        logger.error("CPK {} signed by {} keys", cpk.name, certificates.size)
                        throw InvalidUserDataException("CPK ${cpk.name} must be signed by exactly one key")
                    }
                    val signingKeyHash = digest.digest(certificates.single().publicKey.encoded)
                    cpkDependency.appendElement("signedBy", encoder.encodeToString(signingKeyHash))
                        .setAttribute("algorithm", digest.algorithm)
                }
            }

            // Write CPK dependency information as XML document.
            cpkOutput.get().asFile.bufferedWriter().use(xmlDocument::writeTo)
        } catch (e: Exception) {
            throw (e as? RuntimeException) ?: InvalidUserDataException(e.message ?: "", e)
        }
    }

    @Throws(IOException::class)
    private fun certificatesFor(jar: JarFile): Set<Certificate> {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        return jar.entries().asSequence().flatMapTo(HashSet()) { entry ->
            consume(jar.getInputStream(entry), buffer)
            val certificates = entry.certificates?.toList() ?: emptyList()
            if (certificates.isEmpty() && entry.isSignable) {
                logger.warn("{}:{} is unsigned", Paths.get(jar.name).fileName, entry.name)
            }
            certificates.asSequence()
        }
    }
}
