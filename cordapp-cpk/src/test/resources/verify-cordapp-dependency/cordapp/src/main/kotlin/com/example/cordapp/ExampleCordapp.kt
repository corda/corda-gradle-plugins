package com.example.cordapp

import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction

@Suppress("unused")
class ExampleCordapp : Contract {
    override fun verify(ltx: LedgerTransaction) {
        println(ltx.toString())
    }
}
