package net.corda.plugins

import com.typesafe.config.*
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test

class NetworkParameterOverridesTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun toConfig() {
        val project = ProjectBuilder.builder().build()
        val networkParameterOverrides = createNetworkParameterOverrides(project)

        val config = networkParameterOverrides.toConfig()
        val renderString = config.root().render(ConfigRenderOptions.concise())
        val configFromString = ConfigFactory.parseString(renderString)
        val configObject = configFromString.getObject("networkParameterOverrides")
        val value = configObject.getValue("packageOwnership") as ConfigList
        assertThat(value.size).isEqualTo(1)

        val configValue = value[0] as ConfigObject
        assertThat(configValue.size).isEqualTo(4)

        val expected = ConfigValueFactory.fromMap(
                mapOf("keystore" to "keystore",
                "keystoreAlias" to "keystoreAlias",
                "keystorePassword" to "keystorePassword",
                "packageName" to "packageName"))
        assertThat(configValue["keystore"]).isEqualTo(expected["keystore"])
        assertThat(configValue["keystoreAlias"]).isEqualTo(expected["keystoreAlias"])
        assertThat(configValue["keystorePassword"]).isEqualTo(expected["keystorePassword"])
        assertThat(configValue["packageName"]).isEqualTo(expected["packageName"])
    }

    companion object {
        fun createNetworkParameterOverrides(project: Project): NetworkParameterOverrides {
            val networkParameterOverrides = NetworkParameterOverrides(project)
            val packageOwnership = PackageOwnership("packageName")
            packageOwnership.keystore = "keystore"
            packageOwnership.keystoreAlias = "keystoreAlias"
            packageOwnership.keystorePassword = "keystorePassword"
            networkParameterOverrides.packageOwnership.add(packageOwnership)
            return networkParameterOverrides
        }
    }
}