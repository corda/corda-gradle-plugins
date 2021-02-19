package net.corda.core.contracts

import net.corda.core.transactions.LedgerTransaction


fun interface Contract {
    fun verify(ltx: LedgerTransaction)
}
