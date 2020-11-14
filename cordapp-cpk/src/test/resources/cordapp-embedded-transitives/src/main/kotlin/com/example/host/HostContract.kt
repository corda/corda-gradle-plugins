package com.example.host

import com.example.cordapp.CordappContract
import com.example.embeddable.LogOutput
import net.corda.core.transactions.LedgerTransaction

class HostContract : CordappContract() {
    override fun verify(ltx: LedgerTransaction) {
        super.verify(ltx)
        println(LogOutput.toHexString(ltx.toString().toByteArray()))
    }
}
