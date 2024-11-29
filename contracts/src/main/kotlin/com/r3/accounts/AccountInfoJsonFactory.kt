package com.r3.accounts

import com.r3.accounts.states.AccountInfo
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import java.security.PublicKey
import java.util.*

class AccountInfoJsonFactory : ContractStateVaultJsonFactory<AccountInfo> {
    override fun getStateType(): Class<AccountInfo> = AccountInfo::class.java

    override fun create(state: AccountInfo, jsonMarshallingService: JsonMarshallingService): String {
        return jsonMarshallingService.format(
            AccountInfoJson(
                state.identifier,
                state.metadata,
                state.participants.single().toString(),
                state.accountHash.toString()
            )
        )
    }

    data class AccountInfoJson(
        val identifier: UUID,
        val metadata: String? = null,
        val host: String,
        val accountHash: String,
    )
}