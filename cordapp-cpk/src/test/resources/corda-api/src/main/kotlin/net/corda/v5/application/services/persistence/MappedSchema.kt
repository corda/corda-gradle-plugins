@file:Suppress("PackageDirectoryMismatch")
package net.corda.v5.application.services.persistence

open class MappedSchema(schemaFamily: Class<*>, val version: Int, val mappedTypes: Iterable<Class<*>>) {
    val name: String = schemaFamily.name
}
