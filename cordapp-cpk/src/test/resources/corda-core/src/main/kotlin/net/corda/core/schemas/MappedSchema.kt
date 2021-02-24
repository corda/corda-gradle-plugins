package net.corda.core.schemas

open class MappedSchema(schemaFamily: Class<*>, val version: Int, val mappedTypes: Iterable<Class<*>>) {
    val name: String = schemaFamily.name
}
