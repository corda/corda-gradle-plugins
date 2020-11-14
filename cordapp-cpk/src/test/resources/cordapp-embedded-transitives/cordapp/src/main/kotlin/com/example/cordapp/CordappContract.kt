package com.example.cordapp

import com.example.embeddable.LogOutput
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

open class CordappContract : Contract {
    override fun verify(ltx: LedgerTransaction) {
        LogOutput.log(ltx.toString().toByteArray())
    }
}
