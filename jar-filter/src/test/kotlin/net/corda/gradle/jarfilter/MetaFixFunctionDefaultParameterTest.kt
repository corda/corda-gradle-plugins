package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.recodeMetadataFor
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.isFunction
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.logging.Logger
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions

class MetaFixFunctionDefaultParameterTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixFunctionDefaultParameterTest::class)
        private val hasMandatoryParams
                = isFunction("hasMandatoryParams", String::class, Long::class, Int::class, String::class)
        private val hasOptionalParams
                = isFunction("hasOptionalParams", String::class, String::class)

        lateinit var sourceClass: Class<out Any>
        lateinit var fixedClass: Class<out Any>

        @BeforeAll
        @JvmStatic
        fun setup() {
            val bytecode = recodeMetadataFor<WithFunctionParameters, MetadataTemplate>()
            sourceClass = bytecode.toClass<WithFunctionParameters, Any>()
            fixedClass = bytecode.fixMetadata(logger, pathsOf(WithFunctionParameters::class))
                    .toClass<WithFunctionParameters, Any>()
        }
    }

    @Test
    fun `test source functions have default parameters`() {
        with(sourceClass.kotlin.declaredFunctions) {
            assertThat(size).isEqualTo(2)
            assertThat("source mandatory parameters missing", this, hasItem(hasMandatoryParams))
            assertThat("source optional parameters missing", this, hasItem(hasOptionalParams))
        }

        val sourceUnwanted = sourceClass.kotlin.declaredFunctions.findOrFail("hasMandatoryParams")
        assertThat(sourceUnwanted.call(sourceClass.newInstance(), BIG_NUMBER, NUMBER, MESSAGE))
            .isEqualTo("Long: $BIG_NUMBER, Int: $NUMBER, String: $MESSAGE")

        assertTrue(sourceUnwanted.hasAllOptionalParameters, "All source parameters should be optional")

        val sourceWanted = sourceClass.kotlin.declaredFunctions.findOrFail("hasOptionalParams")
        assertThat(sourceWanted.call(sourceClass.newInstance(), MESSAGE))
            .isEqualTo(MESSAGE)

        assertTrue(sourceWanted.hasAllOptionalParameters, "All source parameters should be optional")
    }

    @Test
    fun `test fixed functions exist`() {
        with(fixedClass.kotlin.declaredFunctions) {
            assertThat(size).isEqualTo(2)
            assertThat("fixed mandatory parameters missing", this, hasItem(hasMandatoryParams))
            assertThat("fixed optional parameters missing", this, hasItem(hasOptionalParams))
        }
    }

    @Test
    fun `test unwanted default parameters are removed`() {
        val fixedMandatory = fixedClass.kotlin.declaredFunctions.findOrFail("hasMandatoryParams")
        assertTrue(fixedMandatory.hasAllMandatoryParameters, "All fixed parameters should be mandatory")
    }

    @Test
    fun `test wanted default parameters are kept`() {
        val fixedOptional = fixedClass.kotlin.declaredFunctions.findOrFail("hasOptionalParams")
        assertTrue(fixedOptional.hasAllOptionalParameters, "All fixed parameters should be optional")
    }

    @Suppress("UNUSED")
    abstract class MetadataTemplate {
        abstract fun hasMandatoryParams(longData: Long = 0, intData: Int = 0, message: String = DEFAULT_MESSAGE): String
        abstract fun hasOptionalParams(message: String = DEFAULT_MESSAGE): String
    }

    private fun <T> Iterable<KFunction<T>>.findOrFail(name: String): KFunction<T> {
        return find { it.name == name } ?: fail("$name missing")
    }
}

@Suppress("UNUSED")
class WithFunctionParameters {
    fun hasMandatoryParams(longData: Long, intData: Int, message: String): String {
        return "Long: $longData, Int: $intData, String: $message"
    }

    fun hasOptionalParams(message: String = DEFAULT_MESSAGE): String = message
}
