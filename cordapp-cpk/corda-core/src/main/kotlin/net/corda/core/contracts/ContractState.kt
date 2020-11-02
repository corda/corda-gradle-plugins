package net.corda.core.contracts

import net.corda.core.identity.AbstractParty

interface ContractState {
    val participants: List<AbstractParty>
}
