package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

class UtilsTest {
    @Test
    fun testRethrowingCheckedException() {
        val ex = assertFailsWith<InvalidUserCodeException> { throw IOException(MESSAGE).asUncheckedException() }
        assertThat(ex)
            .hasMessage(MESSAGE)
            .hasCauseExactlyInstanceOf(IOException::class.java)
    }

    @Test
    fun testRethrowingCheckExceptionWithoutMessage() {
        val ex = assertFailsWith<InvalidUserCodeException> { throw IOException().asUncheckedException() }
        assertThat(ex)
            .hasMessage("")
            .hasCauseExactlyInstanceOf(IOException::class.java)
    }

    @Test
    fun testRethrowingUncheckedException() {
        val ex = assertFailsWith<IllegalArgumentException> { throw IllegalArgumentException(MESSAGE).asUncheckedException() }
        assertThat(ex)
            .hasMessage(MESSAGE)
            .hasNoCause()
    }

    @Test
    fun testRethrowingGradleException() {
        val ex = assertFailsWith<InvalidUserDataException> { throw InvalidUserDataException(MESSAGE).asUncheckedException() }
        assertThat(ex)
            .hasMessage(MESSAGE)
            .hasNoCause()
    }
}
