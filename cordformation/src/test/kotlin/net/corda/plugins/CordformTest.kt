package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Test

class CordformTest : BaseformTest() {
    @Test
    fun `network parameter overrides`() {
        val financeReleaseVersion = cordaBundleVersion

        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithNetworkParameterOverrides.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )
        installResource("testkeystore")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, "corda-finance-workflows-$financeReleaseVersion-cordapp")).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, "corda-finance-contracts-$financeReleaseVersion-cordapp")).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
//
//        ThreadLocalToggleField<SerializationEnvironment>("contextSerializationEnv")
//        net.corda.core.serialization.internal._contextSerializationEnv.set(SerializationEnvironment.with(
//                SerializationFactoryImpl().apply {
//                    registerScheme(AMQPParametersSerializationScheme())
//                },
//                AMQP_P2P_CONTEXT)
//        )
//        val serializedBytes = SerializedBytes<SignedDataWithCert<NetworkParameters>>(getNetworkParameterOverrides(notaryNodeName).toFile().readBytes())
//        val deserializedNetworkParameterOverrides = serializedBytes.deserialize(SerializationDefaults.SERIALIZATION_FACTORY).raw.deserialize()
//        val deserializedPackageOwnership = deserializedNetworkParameterOverrides.packageOwnership
//        assertThat(deserializedPackageOwnership.containsKey("com.mypackagename")).isTrue()
//        assertEquals(Duration.ofDays(2), deserializedNetworkParameterOverrides.eventHorizon)
//        assertEquals(123456, deserializedNetworkParameterOverrides.maxMessageSize)
//        assertEquals(2468, deserializedNetworkParameterOverrides.maxTransactionSize)
//        assertEquals(3, deserializedNetworkParameterOverrides.minimumPlatformVersion)
    }

    @Test
    fun `a node with cordapp dependency - backwards compatibility`() {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithCordappBackwardsCompatibility.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, cordaFinanceWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaFinanceContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `a node that requires an extra command to create schema`() {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithExtraCommandForDbSchema.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(SUCCESS)
        assertThat(getNodeLogFile(notaryNodeName, "node-run-migration.log")).isRegularFile()
        assertThat(getNodeLogFile(notaryNodeName, "node-schema-cordform.log")).isRegularFile()
        assertThat(getNodeLogFile(notaryNodeName, "node-info-gen.log")).isRegularFile()
    }

    @Test
    fun `a node with cordapp dependency`() {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithCordapp.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()
        println(result.output)

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, cordaFinanceWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaFinanceContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `a node with a project cordapp dependency`() {
        val runner = getStandardGradleRunnerFor(
            "deploy-project-cordapp/DeploySingleNodeWithProjectCordapp.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )
        installResource("deploy-project-cordapp/cordapp/build.gradle")

        val result = runner.build()
        println(result.output)

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(SUCCESS)

        assertThat(getNodeCordappCpk(notaryNodeName, localCordappCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, localCordappCpkName)).isRegularFile()
    }

    @Test
    fun `a node with cordapp dependency with OU in name`() {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithCordappWithOU.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()
        val notaryFullName = "${notaryNodeName}_${notaryNodeUnitName}"

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(SUCCESS)
        assertThat(getNodeCordappCpk(notaryFullName, cordaFinanceWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryFullName, cordaFinanceContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryFullName)).isRegularFile()
    }

    @Test
    fun `deploy a node with cordapp config`() {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithCordappConfig.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, cordaFinanceWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaFinanceContractsCpkName)).isRegularFile()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceContractsCpkName)).isRegularFile()
    }

    @Test
    fun `deploy the locally built cordapp with cordapp config`() {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithLocallyBuildCordappAndConfig.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, localCordappCpkName)).isRegularFile()
        assertThat(getNodeCordappConfig(notaryNodeName, localCordappCpkName)).isRegularFile()
    }

    @Test
    fun `regex matching used by verifyAndGetRuntimeJar()`() {
        val jarName = "corda"

        var releaseVersion = "4.3"
        var pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex().pattern
        assertThat("corda-4.3.jar").containsPattern(pattern)
        assertThat("corda-4.3.jar").containsPattern(pattern)
        assertThat("corda-4.3.jarBla").doesNotContainPattern(pattern)
        assertThat("bla\\bla\\bla\\corda-4.3.jar").containsPattern(pattern)
        assertThat("corda-4.3-jdk11.jar").containsPattern(pattern)
        assertThat("corda-4.3jdk11.jar").doesNotContainPattern(pattern)
        assertThat("bla\\bla\\bla\\corda-enterprise-4.3.jar").containsPattern(pattern)
        assertThat("corda-enterprise-4.3.jar").containsPattern(pattern)
        assertThat("corda-enterprise-4.3-jdk11.jar").containsPattern(pattern)

        releaseVersion = "4.3-RC01"
        pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex().pattern
        assertThat("corda-4.3-RC01.jar").containsPattern(pattern)
        assertThat("corda-4.3RC01.jar").doesNotContainPattern(pattern)

        releaseVersion = "4.3.20190925"
        pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex().pattern
        assertThat("corda-4.3.20190925.jar").containsPattern(pattern)
        assertThat("corda-4.3.20190925-TEST.jar").containsPattern(pattern)
    }
}
