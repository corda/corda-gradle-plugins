package com.example.contract.states

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

class ExampleState(val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> = listOf(issuer)
}
