package net.corda.core.contracts

import net.corda.core.transactions.LedgerTransaction

interface Contract {
    fun verify(ltx: LedgerTransaction)
}
