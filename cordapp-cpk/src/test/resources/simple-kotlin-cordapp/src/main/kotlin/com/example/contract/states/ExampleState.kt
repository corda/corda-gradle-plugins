package com.example.contract.states

import com.google.common.collect.ImmutableList
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

class ExampleState(val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> = ImmutableList.of(issuer)
}
