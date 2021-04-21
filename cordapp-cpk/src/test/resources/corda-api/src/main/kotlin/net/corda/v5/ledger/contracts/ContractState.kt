@file:Suppress("PackageDirectoryMismatch")
package net.corda.v5.ledger.contracts

import net.corda.core.identity.AbstractParty

interface ContractState {
    val participants: List<AbstractParty>
}
