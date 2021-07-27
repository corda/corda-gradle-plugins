package net.corda.plugins.cpb

import net.corda.plugins.cpk.GradleProject
import net.corda.plugins.cpk.cordaApiVersion
import net.corda.plugins.cpk.expectedCordappContractVersion
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.TreeMap
import java.util.jar.JarInputStream
import javax.security.auth.x500.X500Principal
import kotlin.streams.asSequence

class CpbTest {
    companion object {
        private const val cordappVersion = "1.2.1"
        private const val hostVersion = "2.0.1"
        private val cordaDevCertPrincipal = X500Principal("CN=Corda Dev Code Signer, OU=R3, O=Corda, L=London, C=GB")


        private lateinit var externalProject: GradleProject
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testDir: Path, reporter: TestReporter) {
            val mavenRepoDir = Files.createDirectory(testDir.resolve("maven"))
            val externalCordappProjectDir = Files.createTempDirectory(testDir.parent, "externalCordapp")
            val cpbProjectDir = Files.createDirectory(testDir.resolve("cpb"))
            externalProject = GradleProject(externalCordappProjectDir, reporter)
                .withTestName("external-cordapp")
                .withSubResource("corda-platform-cordapp/build.gradle")
                .withSubResource("external-cordapp-transitive-dependency/build.gradle")
                .withTaskName("publishAllPublicationsToTestRepository")
                .build("-Pmaven.repository.dir=$mavenRepoDir",
                    "-Pcorda_api_version=$cordaApiVersion",
                    "-Phost_version=$hostVersion",
                    "-Pcordapp_version=$cordappVersion",
                    "-Pcordapp_contract_version=$expectedCordappContractVersion")
            testProject = GradleProject(cpbProjectDir, reporter)
                .withTestName("cordapp-cpb")
                .withSubResource("project-dependency/build.gradle")
                .build("-Pmaven.repository.dir=$mavenRepoDir",
                    "-Pcordapp_version=$cordappVersion",
                    "-Phost_version=$hostVersion",
                    "-Pcordapp_contract_version=$expectedCordappContractVersion")
        }

        @AfterAll
        @JvmStatic
        fun tearDown() = externalProject.delete()

        private fun ByteArray.toHex() = joinToString(separator = "") {
            String.format("%02X", it)
        }

        fun sha256(inputStream : InputStream) : String {
            val md = MessageDigest.getInstance("SHA-256")
            DigestInputStream(inputStream, md).also {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while(it.read(buffer) > 0) {}
            }
            return md.digest().toHex()
        }
    }

    @Test
    fun `Ensure the generated CPB archive contains the project CPK and all of its dependencies`() {
        val allCpks = (Files.list(testProject.buildDir.resolve("cpks"))
            .asSequence() +
            Files.list(testProject.artifactDir).asSequence().filter {
                it.fileName.toString().endsWith(".cpk")
            }).toList()

        val cordaPlatformCordappCpk = allCpks.singleOrNull {
            it.fileName.toString().startsWith("corda-platform-cordapp")
        } ?: Assertions.fail("Corda platform CorDapp CPK not found")

        val expectedCpks = allCpks.filter { it != cordaPlatformCordappCpk }
            .associateByTo(TreeMap(), { it.fileName.toString() }, {Files.newInputStream(it).use(::sha256)})

        val assembledCpbFiles = testProject.artifacts.filter {
            it.fileName.toString().endsWith(".cpb")
        }
        Assertions.assertEquals(1, assembledCpbFiles.size, "Expected a single cpb archive")
        val cpbFile = assembledCpbFiles.first()

        testProject.cpkDependencies.singleOrNull {
            it.name == "com.example.corda-platform-cordapp"
        }?.let {
            Assertions.assertEquals(cordappVersion, it.version)
            Assertions.assertEquals("corda-api", it.type)
        } ?: Assertions.fail("'com.example.corda-platform-cordapp' is expected to be listed in the META-INF/CPKDependencies file")

        val embeddedCpkFiles = TreeMap<String, String>()
        JarInputStream(Files.newInputStream(cpbFile), true).use { jarInputStream ->
            generateSequence(jarInputStream::getNextJarEntry).forEach { jarEntry ->
                when {
                    jarEntry.name.endsWith(".cpk") -> {
                        Assertions.assertEquals(-1, jarEntry.name.indexOf('/'),
                            "All CPK files in a CPB must be in the root directory of the archive, found '${jarEntry.name}' instead")
                        embeddedCpkFiles += jarEntry.name to sha256(jarInputStream)
                    }
                    else -> {
                        /**
                         * Consume the stream so that we can fetch the [JarInputStream] so that we can invoke
                         * [java.util.jar.JarEntry.getCertificates]
                         **/
                        sha256(jarInputStream)
                    }
                }
                if(!jarEntry.name.startsWith("META-INF/")) {
                    Assertions.assertEquals(cordaDevCertPrincipal, (jarEntry.certificates.single() as X509Certificate).subjectX500Principal)
                }
            }
        }
        Assertions.assertFalse(embeddedCpkFiles.containsKey(cordaPlatformCordappCpk.fileName.toString()),
            "Corda platform CorDapp CPK is expected to be excluded from the generated CPB archive " +
                    "as it contains CPK-Type: corda-api in its manifest, it was found instead"
        )
        Assertions.assertEquals(expectedCpks, embeddedCpkFiles)
    }
}
