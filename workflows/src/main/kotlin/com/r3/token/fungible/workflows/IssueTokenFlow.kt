package com.r3.token.fungible.workflows

import com.r3.accounts.states.AccountInfo
import com.r3.token.fungible.contracts.TokenContract
import com.r3.token.fungible.states.Token
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@InitiatingFlow(protocol = "issue-token")
class IssueTokenFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private data class IssueTokenRequest(
        val ownerId: String,
        val issuerId: String,
        val symbol: String,
        val amount: BigDecimal
    )

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("IssueTokenFlow.call() called")

        val request = requestBody.getRequestBodyAs(
            jsonMarshallingService,
            IssueTokenRequest::class.java
        )

        val notaryInfo = notaryLookup.notaryServices.single()

        val myKey = memberLookup.myInfo().ledgerKeys.first()

        val queryIssuerAccount = utxoLedgerService.query("ACCOUNT_INFO_QUERY", AccountInfo::class.java)
            .setParameter("identifier", request.issuerId)
            .setCreatedTimestampLimit(Instant.now())


        val issuerAccountInfo = queryIssuerAccount.execute().results.singleOrNull()
            ?: throw CordaRuntimeException("Issuer Account not found.")
        val issuerKey = issuerAccountInfo.participants.single()
        val issuerHost = memberLookup.lookup(issuerKey) ?: throw CordaRuntimeException("Issuer Host not found.")

        if (issuerHost != memberLookup.myInfo()) {
            throw CordaRuntimeException("Issuer Account should be hosted on initiator node.")
        }

        val queryOwnerAccount = utxoLedgerService.query("ACCOUNT_INFO_QUERY", AccountInfo::class.java)
            .setParameter("identifier", request.ownerId)
            .setCreatedTimestampLimit(Instant.now())
        val ownerAccountInfo = queryOwnerAccount.execute().results.single()
        val ownerKey = ownerAccountInfo.participants.single()
        val ownerHost = memberLookup.lookup(ownerKey) ?: throw CordaRuntimeException("Owner Host not found.")

        val newToken = Token(
            issuerAccount = issuerAccountInfo,
            symbol = request.symbol,
            amount = request.amount,
            participants = listOf(myKey),
            tag = ownerAccountInfo.identifier.toString(),
            ownerAccount = ownerAccountInfo
        )

        val transaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notaryInfo.name)
            .addOutputState(newToken)
            .addCommand(TokenContract.Issue())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .addSignatories(listOf(myKey))
            .toSignedTransaction()

        val sessions = mutableListOf<FlowSession>()

        if (ownerHost != memberLookup.myInfo()) {
            sessions.add(flowMessaging.initiateFlow(ownerHost.name))
        }

        return try {
            utxoLedgerService.finalize(transaction, sessions)
            log.info("Finalization has been finished")
            "Successfully Issued New Token(symbol:${newToken.symbol}, amount:${newToken.amount}) To ${ownerAccountInfo.identifier}"
        } catch (e: Exception) {
            "Flow failed, message: ${e.message}"
        }
    }
}

@InitiatedBy(protocol = "issue-token")
class IssueTokenResponderFlow : ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val states = ledgerTransaction.outputContractStates
                // Check something for SampleToken if you need
            }
            log.info("Finished responder flow - $finalizedSignedTransaction")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}

/*
{
  "clientRequestId": "issue-1",
  "flowClassName": "com.r3.token.fungible.workflows.IssueTokenFlow",
  "requestBody": {
    "issuerId": "df26f856-7437-4b6c-aaa9-3b7bb1810890",
    "ownerId": "4091a28b-0791-4ef7-bf8c-2befe801e5f8",
    "symbol": "USD",
    "amount": 100
  }
}
*/