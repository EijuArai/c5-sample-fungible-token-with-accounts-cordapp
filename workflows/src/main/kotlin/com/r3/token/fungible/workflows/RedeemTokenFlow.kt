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
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@InitiatingFlow(protocol = "redeem-token")
class RedeemTokenFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private data class RedeemTokenRequest(
        val issuerId: String,
        val ownerId: String,
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

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("RedeemTokenFlow.call() called")

        val request = requestBody.getRequestBodyAs(jsonMarshallingService, RedeemTokenRequest::class.java)

        val notaryInfo = notaryLookup.notaryServices.single()

        val myKey = memberLookup.myInfo().ledgerKeys.first()

        val queryIssuerAccount = utxoLedgerService.query("ACCOUNT_INFO_QUERY", AccountInfo::class.java)
            .setParameter("identifier", request.issuerId)
            .setCreatedTimestampLimit(Instant.now())
        val issuerAccountInfo = queryIssuerAccount.execute().results.singleOrNull() ?: throw CordaRuntimeException("Issuer Account not found.")
        val issuerKey = issuerAccountInfo.participants.single()
        val issuerHost = memberLookup.lookup(issuerKey) ?: throw CordaRuntimeException("Issuer Host not found.")

        val queryOwnerAccount = utxoLedgerService.query("ACCOUNT_INFO_QUERY", AccountInfo::class.java)
            .setParameter("identifier", request.ownerId)
            .setCreatedTimestampLimit(Instant.now())
        val ownerAccountInfo = queryOwnerAccount.execute().results.singleOrNull() ?: throw CordaRuntimeException("Owner Account not found.")
        val ownerKey = ownerAccountInfo.participants.single()
        val ownerHost = memberLookup.lookup(ownerKey) ?: throw CordaRuntimeException("Owner Host not found.")

        if (ownerHost != memberLookup.myInfo()) {
            throw CordaRuntimeException("Owner Account should be hosted on initiator node.")
        }

        val issuerHash = issuerAccountInfo.accountHash
        val ownerHash = ownerAccountInfo.accountHash

        val criteria = TokenClaimCriteria(
            Token.tokenType,
            issuerHash,
            notaryInfo.name,
            request.symbol,
            request.amount
        ).apply { this.ownerHash = ownerHash }
        val claim =
            tokenSelection.tryClaim("redeem", criteria) ?: return jsonMarshallingService.format("Insufficient Token Amount")
        val spentTokenRefs = claim.claimedTokens.map { it.stateRef }
        val spentTokenAmount = claim.claimedTokens.sumOf { it.amount }

        val changeAmount = spentTokenAmount - request.amount
        val changeToken = if (changeAmount > BigDecimal(0)) Token(
            request.symbol,
            changeAmount,
            ownerAccountInfo.identifier.toString(),
            issuerAccountInfo,
            ownerAccountInfo,
            listOf(myKey)
        ) else null

        val sessions = mutableListOf<FlowSession>()

        if (issuerHost != memberLookup.myInfo()) {
            sessions.add(flowMessaging.initiateFlow(issuerHost.name))
        }

        val requiredKeys = listOf(myKey, issuerKey).distinct()

        val transaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notaryInfo.name)
            .addInputStates(spentTokenRefs)
            .apply { changeToken?.let { addOutputState(it) } }
            .addCommand(TokenContract.Redeem())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .addSignatories(requiredKeys)
            .toSignedTransaction()

        return try {
            utxoLedgerService.finalize(transaction, sessions)
            log.info("Finalization has been finished")
            "Successfully Redeemed ${request.amount}${request.symbol}"
        } catch (e: Exception) {
            "Flow failed, message: ${e.message}"
        }
    }
}

@InitiatedBy(protocol = "redeem-token")
class RedeemTokenResponderFlow : ResponderFlow {

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
  "clientRequestId": "redeem-1",
  "flowClassName": "com.r3.token.fungible.workflows.RedeemTokenFlow",
  "requestBody": {
    "issuerId": "df26f856-7437-4b6c-aaa9-3b7bb1810890",
    "ownerId": "4091a28b-0791-4ef7-bf8c-2befe801e5f8",
    "symbol": "USD",
    "amount": 50
  }
}
*/