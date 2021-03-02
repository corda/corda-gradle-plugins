@file:Suppress("unused")
package com.example.metadata.schemas

import net.corda.core.node.services.persistence.MappedSchema
import org.hibernate.annotations.Type
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object ExampleSchema

object ExampleSchemaV1 : MappedSchema(
    schemaFamily = ExampleSchema::class.java,
    version = 1,
    mappedTypes = listOf(ExampleEntity::class.java)
) {
    @Entity
    @Table(name = "example_entities")
    class ExampleEntity(
        @Column(name = "example_key_hash", nullable = false)
        var key: String,

        @Column(name = "example_ref", nullable = false)
        @Type(type = "corda-wrapper-binary")
        var ref: ByteArray
    )
}
