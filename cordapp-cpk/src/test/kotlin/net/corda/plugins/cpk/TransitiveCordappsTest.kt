package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import org.osgi.framework.Constants.REQUIRE_CAPABILITY
import java.nio.file.Path

/**
 * Verify that transitive cordapp and cordaProvided dependencies
 * are inherited by downstream CPK projects.
 */
class TransitiveCordappsTest {
    companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val cpk1Version = "1.0-SNAPSHOT"
        private const val cpk2Version = "2.0-SNAPSHOT"
        private const val cpk3Version = "3.0-SNAPSHOT"

        const val ioOsgiVersion = "version=\"[1.4,2)\""
        const val kotlinOsgiVersion = "version=\"[1.4,2)\""
        const val cordaOsgiVersion = "version=\"[5.0,6)\""
        const val cordappOsgiVersion = "version=\"1.0.1\""

        private fun buildProject(
            withPublishing: Boolean,
            testProjectDir: Path,
            reporter: TestReporter
        ): GradleProject {
            return GradleProject(testProjectDir, reporter)
                .withTestName("transitive-cordapps")
                .withSubResource("src/main/kotlin/com/example/transitives/ExampleContract.kt")
                .withSubResource("cpk-one/build.gradle")
                .withSubResource("cpk-two/build.gradle")
                .withSubResource("cpk-three/build.gradle")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Pcordapp_version=$cordappVersion",
                    "-Pcpk1_version=$cpk1Version",
                    "-Pcpk2_version=$cpk2Version",
                    "-Pcpk3_version=$cpk3Version",
                    "-Pwith_publishing=$withPublishing"
                )
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun transitivesTest(
        withPublishing: Boolean,
        @TempDir testProjectDir: Path,
        reporter: TestReporter
    ) {
        val testProject = buildProject(withPublishing, testProjectDir, reporter)

        assertThat(testProject.dependencyConstraints)
            .noneMatch { it.startsWith("cpk-one-${cpk1Version}.jar") }
            .noneMatch { it.startsWith("cpk-two-${cpk2Version}.jar") }
            .noneMatch { it.startsWith("cpk-three-${cpk3Version}.jar") }
            .anyMatch { it.startsWith("commons-io-$commonsIoVersion.jar") }
            .hasSize(1)
        assertThat(testProject.cpkDependencies)
            .contains("com.example.cpk-one,${toOSGi(cpk1Version)}")
            .contains("com.example.cpk-two,${toOSGi(cpk2Version)}")
            .contains("com.example.cpk-three,${toOSGi(cpk3Version)}")
            .hasSize(3)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("Transitive CorDapps", getValue(BUNDLE_NAME))
            assertEquals("com.example.transitive-cordapps", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertThatHeader(getValue(IMPORT_PACKAGE)).containsAll(
                "kotlin;$kotlinOsgiVersion",
                "kotlin.io;$kotlinOsgiVersion",
                "kotlin.jvm.internal;$kotlinOsgiVersion",
                "kotlin.text;$kotlinOsgiVersion",
                "net.corda.core.contracts;$cordaOsgiVersion",
                "net.corda.core.transactions;$cordaOsgiVersion",
                "org.apache.commons.io;$ioOsgiVersion"
            )
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsAll(
                "com.example.transitives;uses:=\"kotlin,net.corda.core.contracts,net.corda.core.transactions\";$cordappOsgiVersion"
            )
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertNull(getValue("Private-Package"))
        }
    }
}