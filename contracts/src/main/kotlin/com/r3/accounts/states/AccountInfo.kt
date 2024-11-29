package com.r3.accounts.states

import com.r3.accounts.contracts.AccountInfoContract
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*

@BelongsToContract(AccountInfoContract::class)
data class AccountInfo(
    val identifier: UUID,
    val metadata: String?,
    val accountHash: SecureHash,
    private val participants: List<PublicKey>
) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}