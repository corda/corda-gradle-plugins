package com.example.cordapp

import com.example.embeddable.LogOutput
import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction

open class CordappContract : Contract {
    override fun verify(ltx: LedgerTransaction) {
        LogOutput.log(ltx.toString().toByteArray())
    }
}
