@file:Suppress("PackageDirectoryMismatch")
package com.example.constraint

import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction
import org.apache.commons.codec.Resources
import org.testing.compress.ExampleZip
import org.testing.io.ExampleStream

@Suppress("unused")
class ConstraintContract : Contract {
    override fun verify(ltx: LedgerTransaction) {
        ExampleZip(ExampleStream(Resources.getInputStream("testing")))
    }
}
