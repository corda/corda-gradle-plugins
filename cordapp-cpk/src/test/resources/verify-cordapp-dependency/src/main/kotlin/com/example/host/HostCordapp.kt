package com.example.host

import com.example.cordapp.ExampleCordapp
import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction

class HostCordapp : Contract {
    override fun verify(ltx: LedgerTransaction) {
        ExampleCordapp().verify(ltx)
    }
}
