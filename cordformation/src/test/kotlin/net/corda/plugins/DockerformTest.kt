package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.nio.file.Path

class DockerformTest : BaseformTest() {

    private fun getDockerCompose(): Path = testProjectDir.resolve("build").resolve("nodes").resolve("docker-compose.yml")

    @Test
    fun `fail to deploy a node with cordapp dependency with missing dockerImage tag`() {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithCordappWithDocker.gradle",
            "prepareDockerNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.buildAndFail()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Caused by: org.gradle.api.InvalidUserDataException: No value has been specified for property 'dockerImage'.")
    }

    @Test
    fun `deploy a node with cordapp dependency and network configuration` () {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithCordappWithNetworkConfigWithDocker.gradle",
            "prepareDockerNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a three node cordapp` () {
        val runner = getStandardGradleRunnerFor(
            "DeployThreeNodeCordappWithExternalDBSettingsWithDocker.gradle",
            "prepareDockerNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val bigCorporationNodeName = "BigCorporation"

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
        assertThat(getNodeCordappCpk(bankNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(bankNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bankNodeName)).isRegularFile()
        assertThat(getNodeCordappCpk(bigCorporationNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(bigCorporationNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bigCorporationNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a three node cordapp with network configuration` () {
        val runner = getStandardGradleRunnerFor(
            "DeployThreeNodeCordappWithExternalDBSettingsWithNetworkConfigWithDocker.gradle",
            "prepareDockerNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val bigCorporationNodeName = "BigCorporation"

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
        assertThat(getNodeCordappCpk(bankNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(bankNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bankNodeName)).isRegularFile()
        assertThat(getNodeCordappCpk(bigCorporationNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(bigCorporationNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bigCorporationNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a node with cordapp dependency with db settings`() {
        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithExternalDBSettingsWithDocker.gradle",
            "prepareDockerNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Suppress("unchecked_cast")
    @Test
    fun `deploy two nodes with cordapp dependencies`() {
        val runner = getStandardGradleRunnerFor(
            "DeployTwoNodeCordappWithDocker.gradle",
            "prepareDockerNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappCpk(notaryNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
        assertThat(getNodeCordappCpk(bankNodeName, cordaWorkflowsCpkName)).isRegularFile()
        assertThat(getNodeCordappCpk(bankNodeName, cordaContractsCpkName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bankNodeName)).isRegularFile()

        assertThatConfig(getNodeConfig(notaryNodeName))
            .hasPath("rpcSettings.address", "notary-service:10003")
            .hasPath("rpcSettings.adminAddress", "notary-service:10043")

        assertThatConfig(getNodeConfig(bankNodeName))
            .hasPath("rpcSettings.address", "bankofcorda:10006")
            .hasPath("rpcSettings.adminAddress", "bankofcorda:10046")

        val dockerComposePath = getDockerCompose()

        val yaml = dockerComposePath.toFile().bufferedReader().use { reader ->
            Yaml().load(reader) as Map<String, Any>
        }
        assertThat(yaml).containsKey("services")

        val services = yaml["services"] as Map<String, Any>
        assertThat(services).containsKey("notary-service")
        assertThat(services).containsKey("bankofcorda")

        val notaryService = services["notary-service"] as Map<String, Any>
        assertThat(notaryService).containsKey("ports")
        val notaryServicePortsList = notaryService["ports"] as List<Any>
        assertThat(notaryServicePortsList).contains("22234:22234")

        val bankOfCorda = services["bankofcorda"] as Map<String, Any>
        assertThat(bankOfCorda).containsKey("ports")
        val bankOfCordaPortsList = bankOfCorda["ports"] as List<Any>
        assertThat(bankOfCordaPortsList).contains("22235:22235")
    }

    @Suppress("unchecked_cast")
    @Test
    fun `deploy two nodes with Docker and external service`() {
        val runner = getStandardGradleRunnerFor(
            "DeployTwoNodeCordappWithExternalService.gradle",
            "prepareDockerNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()
        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val dockerComposePath = getDockerCompose()

        val yaml = dockerComposePath.toFile().bufferedReader().use { reader ->
            Yaml().load(reader) as Map<String, Any>
        }

        val services = yaml["services"] as Map<String, Any>

        val external = services["example-service"] as Map<String, Any>
        assertThat(external).containsOnlyKeys("container_name","volumes","image","ports","privileged","command","environment")

        val name = external["container_name"] as String
        assertThat(name).isEqualTo("example-service")

        val image = external["image"] as String
        assertThat(image).isEqualTo("docker.io/bitnami/java:latest")

        val volumes = external["volumes"] as List<String>
        assertThat(volumes).contains("C:\\Projects\\gs-rest-service\\target\\CordaDevTestAPI-0.0.1-SNAPSHOT.jar:/home/CordaDevTestAPI.jar")

        val environment = external["environment"] as List<String>
        assertThat(environment).contains("rpcUsername=user1")
        assertThat(environment).contains("rpcPassword=pass2")

        val ports = external["ports"] as List<String>
        assertThat(ports).contains("8080:8080")
        assertThat(ports).contains("8000:8000")

        val cmd = external["command"] as String
        assertThat(cmd).isEqualTo("bash -c \"cd home && java -jar CordaDevTestAPI.jar\"")

        val privileged = external["privileged"] as Boolean
        assertThat(privileged).isTrue()
    }

    @Suppress("unchecked_cast")
    @Test
    fun `deploy two nodes with Docker and external service (minimal options)`() {
        val runner = getStandardGradleRunnerFor(
            "DeployTwoNodeCordappWithExternalServiceNoOption.gradle",
            "prepareDockerNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()
        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val dockerComposePath = getDockerCompose()

        val yaml = dockerComposePath.toFile().bufferedReader().use { reader ->
            Yaml().load(reader) as Map<String, Any>
        }

        val services = yaml["services"] as Map<String, Any>

        val external = services["external-service"] as Map<String, Any>
        assertThat(external).containsOnlyKeys("container_name","image","ports")

        val name = external["container_name"] as String
        assertThat(name).isEqualTo("external-service")

        val image = external["image"] as String
        assertThat(image).isEqualTo("docker/test/customTomcat")

        val ports = external["ports"] as List<String>
        assertThat(ports).contains("8080:8080")

    }
}
