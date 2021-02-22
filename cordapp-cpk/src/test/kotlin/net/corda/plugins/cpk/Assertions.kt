@file:JvmName("Assertions")
package net.corda.plugins.cpk

import aQute.bnd.header.OSGiHeader
import aQute.bnd.header.Parameters
import org.assertj.core.api.Assertions.assertThat

class AssertHeaders(private val parameters: Parameters) {
    constructor(header: String) : this(OSGiHeader.parseHeader(header))

    fun containsAll(vararg elements: String): AssertHeaders {
        assertThat(actualElements).contains(*elements)
        return this
    }

    fun containsPackage(packageName: String, vararg attributes: String): AssertHeaders {
        assertThat(parameters.keys).contains(packageName)
        assertThat(parameters[packageName].map(Any::toString)).contains(*attributes)
        return this
    }

    fun hasPackageVersion(packageName: String): AssertHeaders {
        assertThat(parameters[packageName].version).isNotNull()
        return this
    }

    private val actualElements: List<String>
        get() = parameters.entries.map { "${it.key};${it.value}" }
}

fun assertThatHeader(header: String) = AssertHeaders(header)