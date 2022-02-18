package net.corda.plugins.cpb

import net.corda.plugins.cpk.GradleProject
import net.corda.plugins.cpk.cordaApiVersion
import net.corda.plugins.cpk.digestFor
import net.corda.plugins.cpk.expectedCordappContractVersion
import net.corda.plugins.cpk.hashFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.util.TreeMap
import java.util.jar.JarInputStream
import javax.security.auth.x500.X500Principal
import kotlin.streams.asSequence

@TestInstance(PER_CLASS)
class CpbTest {
    private companion object {
        private const val platformCordappVersion = "2.3.4"
        private const val cordappVersion = "1.2.1"
        private val cordaDevCertPrincipal = X500Principal("CN=Corda Dev Code Signer, OU=R3, O=Corda, L=London, C=GB")
    }

    private lateinit var externalProject: GradleProject
    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(
        @TempDir externalCordappProjectDir: Path,
        @TempDir testDir: Path,
        reporter: TestReporter
    ) {
        val mavenRepoDir = Files.createDirectory(testDir.resolve("maven"))
        val cpbProjectDir = Files.createDirectory(testDir.resolve("cpb"))
        externalProject = GradleProject(externalCordappProjectDir, reporter)
            .withTestName("external-cordapp")
            .withSubResource("corda-platform-cordapp/build.gradle")
            .withSubResource("external-cordapp-transitive-dependency/build.gradle")
            .withTaskName("publishAllPublicationsToTestRepository")
            .build("-Pmaven_repository_dir=$mavenRepoDir",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pplatform_cordapp_version=$platformCordappVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion")
        testProject = GradleProject(cpbProjectDir, reporter)
            .withTestName("cordapp-cpb")
            .withSubResource("project-dependency/build.gradle")
            .build("-Pmaven_repository_dir=$mavenRepoDir",
                "-Pplatform_cordapp_version=$platformCordappVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion")
    }

    private fun ByteArray.toHex() = joinToString(separator = "") {
        String.format("%02X", it)
    }

    private fun sha256(inputStream : InputStream) : String {
        return digestFor(algorithmName = "SHA-256").hashFor(inputStream).toHex()
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
        } ?: fail("Corda platform CorDapp CPK not found")

        val expectedCpks = allCpks.filter { it != cordaPlatformCordappCpk }
            .associateByTo(TreeMap(), { it.fileName.toString() }, { Files.newInputStream(it).use(::sha256) })

        val assembledCpbFiles = testProject.artifacts.filter {
            it.fileName.toString().endsWith(".cpb")
        }
        assertEquals(1, assembledCpbFiles.size, "Expected a single cpb archive")
        val cpbFile = assembledCpbFiles.first()

        testProject.cpkDependencies.singleOrNull {
            it.name == "net.corda.corda-platform-cordapp"
        }?.let {
            assertEquals(platformCordappVersion, it.version)
            assertEquals("corda-api", it.type)
        } ?: fail("'net.corda.corda-platform-cordapp' is expected to be listed in the META-INF/CPKDependencies file")

        val embeddedCpkFiles = TreeMap<String, String>()
        JarInputStream(Files.newInputStream(cpbFile), true).use { jarInputStream ->
            assertEquals("customName", jarInputStream.manifest.mainAttributes.getValue("Corda-CPB-Name"))
            assertEquals("customVersion", jarInputStream.manifest.mainAttributes.getValue("Corda-CPB-Version"))
            generateSequence(jarInputStream::getNextJarEntry).forEach { jarEntry ->
                when {
                    jarEntry.name.endsWith(".cpk") -> {
                        assertEquals(-1, jarEntry.name.indexOf('/'),
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
                if (!jarEntry.name.startsWith("META-INF/")) {
                    assertEquals(cordaDevCertPrincipal, (jarEntry.certificates.single() as X509Certificate).subjectX500Principal)
                }
            }
        }
        assertFalse(embeddedCpkFiles.containsKey(cordaPlatformCordappCpk.fileName.toString()),
            "Corda platform CorDapp CPK is expected to be excluded from the generated CPB archive " +
                    "as it contains CPK-Type: corda-api in its manifest, it was found instead"
        )
        assertEquals(expectedCpks, embeddedCpkFiles)
    }
}
