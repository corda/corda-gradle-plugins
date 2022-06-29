@file:Suppress("PackageDirectoryMismatch")
package com.example.cpk.platform

import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ExampleContract : Contract {
    private val logger: Logger = LoggerFactory.getLogger(ExampleContract::class.java)

    override fun verify(ltx: LedgerTransaction) {
        logger.info("LTX: {}", ltx)
    }
}
