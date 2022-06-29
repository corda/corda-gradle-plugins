@file:Suppress("PackageDirectoryMismatch")
package net.corda.v5.ledger.contracts

import net.corda.v5.ledger.transactions.LedgerTransaction

fun interface Contract {
    fun verify(ltx: LedgerTransaction)
}
