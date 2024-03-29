@file:Suppress("unused", "PackageDirectoryMismatch")
package com.example.contract.states

import com.google.common.collect.ImmutableList
import net.corda.v5.application.identity.AbstractParty
import net.corda.v5.ledger.contracts.ContractState

class ExampleState(val issuer: AbstractParty) : ContractState {
    override fun getParticipants(): List<AbstractParty> {
        return ImmutableList.of(issuer)
    }
}
