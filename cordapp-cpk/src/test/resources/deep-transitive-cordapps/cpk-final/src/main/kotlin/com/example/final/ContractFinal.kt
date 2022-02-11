@file:Suppress("PackageDirectoryMismatch")
package com.example.final

import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction

class ContractFinal : Contract {
    override fun verify(ltx: LedgerTransaction) {
    }
}
