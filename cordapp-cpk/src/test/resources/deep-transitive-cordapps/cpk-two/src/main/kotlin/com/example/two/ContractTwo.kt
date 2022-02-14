@file:Suppress("PackageDirectoryMismatch")
package com.example.two

import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction

class ContractTwo : Contract {
    override fun verify(ltx: LedgerTransaction) {
    }
}
