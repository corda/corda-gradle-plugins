@file:Suppress("PackageDirectoryMismatch")
package com.example.one

import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction

class ContractOne : Contract {
    override fun verify(ltx: LedgerTransaction) {
    }
}
