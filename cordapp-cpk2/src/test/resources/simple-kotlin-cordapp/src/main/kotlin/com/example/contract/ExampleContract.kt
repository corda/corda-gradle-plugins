package com.example.contract

import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction
import org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream

class ExampleContract : Contract {
    override fun verify(ltx: LedgerTransaction) {
        ByteArrayInputStream("Hello Corda!".toByteArray()).use { input ->
            IOUtils.copy(input, System.out)
        }
    }
}
