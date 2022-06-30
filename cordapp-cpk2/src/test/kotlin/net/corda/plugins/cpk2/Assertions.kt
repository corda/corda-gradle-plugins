@file:JvmName("Assertions")
package net.corda.plugins.cpk2

import aQute.bnd.header.OSGiHeader
import aQute.bnd.header.Parameters
import org.assertj.core.api.Assertions.assertThat

@Suppress("unused")
class AssertHeaders(private val parameters: Parameters) {
    constructor(header: String) : this(OSGiHeader.parseHeader(header))

    fun containsAll(vararg elements: String): AssertHeaders {
        assertThat(actualElements).contains(*elements)
        return this
    }

    fun containsExactly(vararg elements: String): AssertHeaders {
        assertThat(actualElements).containsExactly(*elements)
        return this
    }

    fun containsPackageWithAttributes(packageName: String, vararg attributes: String): AssertHeaders {
        assertThat(parameters.keys).contains(packageName)
        val packageAttributes = parameters[packageName].map(Any::toString)
        if (attributes.isEmpty()) {
            assertThat(packageAttributes).isEmpty()
        } else {
            assertThat(packageAttributes).containsExactlyInAnyOrder(*attributes)
        }
        return this
    }

    fun doesNotContainPackage(vararg packageNames: String): AssertHeaders {
        assertThat(parameters.keys).doesNotContain(*packageNames)
        return this
    }

    private val actualElements: List<String>
        get() = parameters.entries.map { "${it.key};${it.value}" }
}

fun assertThatHeader(header: String) = AssertHeaders(header)
