@file:Suppress("unused")
package com.example.metadata.contracts

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.util.Collections.unmodifiableList

class ExampleContract : Contract {
    override fun verify(ltx: LedgerTransaction) {
        @Suppress("ObjectLiteralToLambda")
        val innerContract = object : Contract {
            override fun verify(ltx: LedgerTransaction) {
                println(ltx.toString())
            }
        }
        innerContract.verify(ltx)

        val lambdaContract = Contract { l -> println(l.toString()) }
        lambdaContract.verify(ltx)
    }

    class NestedContract : Contract {
        override fun verify(ltx: LedgerTransaction) = throw UnsupportedOperationException(this::class.java.name)
    }

    inner class InnerContract : Contract {
        override fun verify(ltx: LedgerTransaction) = throw UnsupportedOperationException(this::class.java.name)
    }
}

class ExampleState(val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> = unmodifiableList(listOf(issuer))
}
