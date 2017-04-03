package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.client.mock.int
import net.corda.client.mock.pickOne
import net.corda.client.mock.replicate
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.contracts.asset.DUMMY_CASH_ISSUER_KEY
import net.corda.core.contracts.DummyContract
import net.corda.core.flows.FlowException
import net.corda.core.messaging.startFlow
import net.corda.core.success
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.FinalityFlow
import net.corda.loadtest.LoadTest
import net.corda.loadtest.NodeHandle
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("NotaryTest")

data class NotariseCommand(val issueTx: SignedTransaction, val moveTx: SignedTransaction, val node: NodeHandle)

val dummyNotarisationTest = LoadTest<NotariseCommand, Unit>(
        "Notarising dummy transactions",
        generate = { _, _ ->
            val generateTx = Generator.pickOne(simpleNodes).bind { node: NodeHandle ->
                Generator.int().map {
                    val issueTx = DummyContract.generateInitial(it, notary.info.notaryIdentity, DUMMY_CASH_ISSUER).apply {
                        signWith(DUMMY_CASH_ISSUER_KEY)
                    }
                    val asset = issueTx.toWireTransaction().outRef<DummyContract.SingleOwnerState>(0)
                    val moveTx = DummyContract.move(asset, DUMMY_CASH_ISSUER.party.owningKey).apply {
                        signWith(DUMMY_CASH_ISSUER_KEY)
                    }
                    NotariseCommand(issueTx.toSignedTransaction(false), moveTx.toSignedTransaction(false), node)
                }
            }
            Generator.replicate(10, generateTx)
        },
        interpret = { _, _ -> },
        execute = { (issueTx, moveTx, node) ->
            try {
                val proxy = node.connection.proxy
                val issueFlow = proxy.startFlow(::FinalityFlow, issueTx)
                issueFlow.returnValue.success {
                    val moveFlow = proxy.startFlow(::FinalityFlow, moveTx)
                }
            } catch (e: FlowException) {
                log.error("Failure", e)
            }
        },
        gatherRemoteState = {}
)
