@file:Suppress("PackageDirectoryMismatch")
package com.example.schema

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
import net.corda.v5.persistence.MappedSchema
import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction

class SampleContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}

data class Sample(val names: Set<String>)

object SampleBase

object SampleSchema : MappedSchema(SampleBase::class.java, 1, listOf(PersistentSampleState::class.java)) {
    @Entity
    @Table(name = "sample_states")
    class PersistentSampleState(
        @Column(name = "name")
        var name: String
    )
}
