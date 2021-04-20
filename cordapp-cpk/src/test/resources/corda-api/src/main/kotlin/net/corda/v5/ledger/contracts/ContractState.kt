@file:Suppress("PackageDirectoryMismatch")
package net.corda.v5.ledger.contracts

import net.corda.v5.application.identity.AbstractParty

interface ContractState {
    val participants: List<AbstractParty>
}
