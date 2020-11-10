package com.example.host

import com.example.cordapp.ExampleCordapp
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class HostCordapp : Contract {
    override fun verify(ltx: LedgerTransaction) {
        ExampleCordapp().verify(ltx)
    }
}
