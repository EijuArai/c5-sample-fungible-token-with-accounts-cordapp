package com.r3.token.fungible.workflows

import com.r3.accounts.states.AccountInfo
import com.r3.token.fungible.states.Token
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.token.selection.TokenBalanceCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import org.slf4j.LoggerFactory
import java.time.Instant

@InitiatingFlow(protocol = "get-token-balance")
@Suppress("unused")
class GetTokenBalanceFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private data class GetBalanceRequest(
        val issuerId: String,
        val ownerId: String,
        val symbol: String
    )

    @CordaInject
    private lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("GetBalanceFlow.call() called")

        val request = requestBody.getRequestBodyAs(jsonMarshallingService, GetBalanceRequest::class.java)
        
        val queryOwnerAccount = utxoLedgerService.query("ACCOUNT_INFO_QUERY", AccountInfo::class.java)
            .setParameter("identifier", request.ownerId)
            .setCreatedTimestampLimit(Instant.now())
        val ownerAccountInfo = queryOwnerAccount.execute().results.single()

        val queryIssuerAccount = utxoLedgerService.query("ACCOUNT_INFO_QUERY", AccountInfo::class.java)
            .setParameter("identifier", request.issuerId)
            .setCreatedTimestampLimit(Instant.now())
        val issuerAccountInfo = queryIssuerAccount.execute().results.single()

        val notary = notaryLookup.notaryServices.single()

//        val tokenBalance = try {
//            utxoLedgerService.query("TOKEN_QUERY", Token::class.java)
//                .setParameter("symbol", request.symbol)
//                .setParameter("ownerHash", ownerAccountInfo.accountHash.toString())
//                .setParameter("issuerHash", issuerAccountInfo.accountHash.toString())
//                .execute()
//                .results
//                .map { it.quantity }
//                .sum()
//        } catch (e: NoSuchElementException) {
//            NumericDecimal(BigDecimal.ZERO)
//        }

        val criteria =
            TokenBalanceCriteria(
                Token::class.java.name.toString(),
                issuerAccountInfo.accountHash,
                notary.name,
                request.symbol,
            ).apply { this.ownerHash = ownerAccountInfo.accountHash }

        val tokenBalance = tokenSelection.queryBalance(criteria) ?: throw CordaRuntimeException("Failed to query token balance")

        log.info("Querying Token has been finished")

        return ("Token balance of " + request.ownerId + " is " + tokenBalance.totalBalance + request.symbol)
    }
}
/*
{
  "clientRequestId": "get-1",
  "flowClassName": "com.r3.token.fungible.workflows.GetTokenBalanceFlow",
  "requestBody": {
    "issuerId": "df26f856-7437-4b6c-aaa9-3b7bb1810890",
    "ownerId": "4091a28b-0791-4ef7-bf8c-2befe801e5f8",
    "symbol": "USD"
  }
}
*/