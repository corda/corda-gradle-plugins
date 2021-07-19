@file:Suppress("PackageDirectoryMismatch")
package com.example.host

import com.example.library.ExternalLibrary;
import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.transactions.LedgerTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
class CordappHostContract @Activate constructor(
    @Reference
    private val library: ExternalLibrary
) : Contract {
    override fun verify(ltx: LedgerTransaction) {
        library.apply(ltx.toString())
    }
}
