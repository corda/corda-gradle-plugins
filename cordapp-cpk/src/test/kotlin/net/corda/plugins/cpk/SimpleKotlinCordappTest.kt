package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import org.osgi.framework.Constants.REQUIRE_CAPABILITY
import java.nio.file.Path

@TestInstance(PER_CLASS)
class SimpleKotlinCordappTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val guavaVersion = "29.0-jre"

        private const val ioOsgiVersion = "version=\"[1.4,2)\""
        private const val guavaOsgiVersion = "version=\"[29.0,30)\""
        private const val kotlinOsgiVersion = "version=\"[1.4,2)\""
        private const val cordaOsgiVersion = "version=\"[5.0,6)\""
        private const val cordappOsgiVersion = "version=\"1.0.1\""
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("simple-kotlin-cordapp")
            .withSubResource("src/main/kotlin/com/example/contract/ExampleContract.kt")
            .withSubResource("src/main/kotlin/com/example/contract/states/ExampleState.kt")
            .build(
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pguava_version=$guavaVersion"
            )
    }

    @Test
    fun simpleTest() {
        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.fileName == "commons-io-$commonsIoVersion.jar" }
            .anyMatch { it.fileName == "guava-$guavaVersion.jar" }
            .allMatch { it.hash.isSHA256 }
            .hasSizeGreaterThanOrEqualTo(2)
        assertThat(testProject.cpkDependencies).isEmpty()

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()
        assertThat(cordapp.hashOfEntry(CPK_DEPENDENCIES))
            .isEqualTo(testProject.cpkDependenciesHash)
        assertThat(cordapp.hashOfEntry(DEPENDENCY_CONSTRAINTS))
            .isEqualTo(testProject.dependencyConstraintsHash)

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals(testPlatformVersion, getValue(CORDAPP_PLATFORM_VERSION))
            assertEquals("Simple Kotlin", getValue(BUNDLE_NAME))
            assertEquals("com.example.simple-kotlin-cordapp", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertThatHeader(getValue(IMPORT_PACKAGE)).containsAll(
                "com.google.common.collect;$guavaOsgiVersion",
                "kotlin;$kotlinOsgiVersion",
                "kotlin.io;$kotlinOsgiVersion",
                "kotlin.jvm.internal;$kotlinOsgiVersion",
                "kotlin.text;$kotlinOsgiVersion",
                "net.corda.v5.application.identity;$cordaOsgiVersion",
                "net.corda.v5.ledger.contracts;$cordaOsgiVersion",
                "net.corda.v5.ledger.transactions;$cordaOsgiVersion",
                "org.apache.commons.io;$ioOsgiVersion"
            )
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsAll(
                "com.example.contract;uses:=\"kotlin,net.corda.v5.ledger.contracts,net.corda.v5.ledger.transactions\";$cordappOsgiVersion",
                "com.example.contract.states;uses:=\"kotlin,net.corda.v5.application.identity,net.corda.v5.ledger.contracts\";$cordappOsgiVersion"
            )
            assertThatHeader(getValue("Private-Package")).containsAll(
                "com.google.errorprone.annotations;",
                "com.google.errorprone.annotations.concurrent;",
                "com.google.j2objc.annotations;"
            )
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
        }
    }
}
