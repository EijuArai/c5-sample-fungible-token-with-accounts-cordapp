package com.r3.accounts.workflows

import com.r3.accounts.AccountInfoJsonFactory
import com.r3.accounts.contracts.AccountInfoCommands
import com.r3.accounts.states.AccountInfo
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@InitiatingFlow(protocol = "create-account")
class CreateAccount : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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
    lateinit var digestService: DigestService

    private data class CreateAccountRequest(
        val metadata: String?,
        val shareParty: String?
    )

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("CreateAccount.call() called")

        val request = requestBody.getRequestBodyAs(
            jsonMarshallingService,
            CreateAccountRequest::class.java
        )

        val notaryInfo = notaryLookup.notaryServices.single()

        val myKey = memberLookup.myInfo().ledgerKeys.first()

        val accountUuid = UUID.randomUUID()

        val accountHash = digestService.hash(accountUuid.toString().byteInputStream(), DigestAlgorithmName.SHA2_256)

        val newAccount = AccountInfo(
            identifier = accountUuid,
            metadata = request.metadata,
            accountHash = accountHash,
            participants = listOf(myKey)
        )

        val transaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notaryInfo.name)
            .addOutputState(newAccount)
            .addCommand(AccountInfoCommands.Create())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .addSignatories(listOf(myKey))
            .toSignedTransaction()

        val sessions = mutableListOf<FlowSession>()

        if (request.shareParty != null) {
            val shareParty = memberLookup.lookup(MemberX500Name.parse(request.shareParty)) ?:
            throw CordaRuntimeException("MemberLookup can't find otherMember specified in flow arguments.")
            sessions.add(flowMessaging.initiateFlow(shareParty.name))
        }

        return try {
            utxoLedgerService.finalize(transaction, sessions)
            log.info("Finalization has been finished")
            "Account Has Been Successfully Created With id:$accountUuid, metadata: ${request.metadata}"
        } catch (e: Exception) {
            "Flow failed, message: ${e.message}"
        }
    }
}

@InitiatedBy(protocol = "create-account")
class CreateAccountResponderFlow : ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.outputContractStates.first() as AccountInfo
                // Check something for AccountInfo if you need
            }
            log.info("Finished responder flow - $finalizedSignedTransaction")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}

/*
{
  "clientRequestId": "create-1",
  "flowClassName": "com.r3.accounts.workflows.CreateAccount",
  "requestBody": {
    "metadata": "issuer",
    "shareParty": "CN=Bob,OU=Test Dept,O=R3,L=London,C=GB"
  }
}
*/