@file:Suppress("PackageDirectoryMismatch")
package net.corda.core.node.services.persistence

open class MappedSchema(schemaFamily: Class<*>, val version: Int, val mappedTypes: Iterable<Class<*>>) {
    val name: String = schemaFamily.name
}
