package com.example.cordapp

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

@Suppress("unused")
class ExampleCordapp : Contract {
    override fun verify(ltx: LedgerTransaction) {
        println(ltx.toString())
    }
}
