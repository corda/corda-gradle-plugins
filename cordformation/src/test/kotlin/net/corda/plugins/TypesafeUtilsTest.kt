package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class TypesafeUtilsTest {

    private val urlArgs: Config = ConfigFactory.empty()
            .withValue("DBHOSTNAME", ConfigValueFactory.fromAnyRef("localhost"))
            .withValue("DBPORT", ConfigValueFactory.fromAnyRef(5432))
            .withValue("DBNAME", ConfigValueFactory.fromAnyRef("mydb"))
            .withValue("DBSCHEMA", ConfigValueFactory.fromAnyRef("myschema"))

    @Test
    fun `check correct encoding and typesafe substitution in URL template with valid expected value`() {

        val url = "jdbc:postgresql://\${DBHOSTNAME}:\${DBPORT}/\${DBNAME}?currentSchema=\${DBSCHEMA}"

        val urlConfig = ConfigFactory
                .parseString("DBURL=${TypesafeUtils.encodeString(url)}")
                .resolveWith(urlArgs)

        assertThat(urlConfig.hasPath("DBURL")).isEqualTo(true)
        assertThat(urlConfig.getString("DBURL")).isEqualTo("jdbc:postgresql://localhost:5432/mydb?currentSchema=myschema")
    }

    @Test
    fun `check correct encoding and typesafe substitution in URL template with invalid expected value`() {

        val url = "jdbc:postgresql://\${DBHOSTNAME}:\${DBPORT}/\${DBNAME}?currentSchema=\${DBSCHEMA}"

        val urlConfig = ConfigFactory
                .parseString("DBURL=${TypesafeUtils.encodeString(url)}")
                .resolveWith(urlArgs)

        assertThat(urlConfig.hasPath("DBURL")).isEqualTo(true)
        assertThat(urlConfig.getString("DBURL")).isNotEqualTo("jdbc:postgresql://localhost:5432/mydb?currentSchema=myschema2")
    }

    @Test
    fun `assert that no substitution by typesafe with valid URL template but incorrect placeholders`() {

        val url = "jdbc:postgresql://\${DBHOSTNAME2}:\${DBPORT2}"

        assertFailsWith<ConfigException.UnresolvedSubstitution> {
            ConfigFactory.parseString("DBURL=${TypesafeUtils.encodeString(url)}")
                    .resolveWith(urlArgs)
        }
    }

    @Test
    fun `assert that no substitution by typesafe with valid URL template and no placeholders`() {

        val url = "jdbc:postgresql://localhost:5432"

        val urlConfig = ConfigFactory
                .parseString("DBURL=${TypesafeUtils.encodeString(url)}")
                .resolveWith(urlArgs)

        assertThat(urlConfig.hasPath("DBURL")).isEqualTo(true)
        assertThat(urlConfig.getString("DBURL")).isEqualTo("jdbc:postgresql://localhost:5432")
    }

    @Test
    fun `assert that no substitution by typesafe with invalid URL template and no placeholders`() {

        val url = "jdbc:postgresql://\${DBHO\${STNAME2}:\${DBPORT2}"

        assertFailsWith<ConfigException.Parse> {
            ConfigFactory.parseString("DBURL=${TypesafeUtils.encodeString(url)}")
                    .resolveWith(urlArgs)
        }
    }

    @Test
    fun `check text after last substitution is preserved`() {
        val url = "jdbc:postgresql://\${DBHOSTNAME}:\${DBPORT}?parameter=true"

        val config = ConfigFactory.parseString("DBURL=${TypesafeUtils.encodeString(url)}")
                .resolveWith(urlArgs)
        assertThat(config.hasPath("DBURL")).isTrue()
        assertThat(config.getString("DBURL")).isEqualTo("jdbc:postgresql://localhost:5432?parameter=true")
    }
}