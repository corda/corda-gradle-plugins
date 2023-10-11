@file:Suppress("unused", "PackageDirectoryMismatch")
package com.example.name.schemas

import net.corda.v5.persistence.MappedSchema
import org.hibernate.annotations.Type
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object ExampleSchema

object ExampleSchemaV1 : MappedSchema(ExampleSchema::class.java, 1, listOf(ExampleEntity::class.java)) {
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
