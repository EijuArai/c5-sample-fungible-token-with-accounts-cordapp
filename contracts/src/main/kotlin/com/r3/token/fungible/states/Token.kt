package com.r3.token.fungible.states

import com.r3.accounts.states.AccountInfo
import com.r3.corda.ledger.utxo.fungible.FungibleState
import com.r3.corda.ledger.utxo.fungible.NumericDecimal
import com.r3.token.fungible.contracts.TokenContract
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.math.BigDecimal
import java.security.PublicKey

@BelongsToContract(TokenContract::class)
data class Token(
    val symbol: String,
    val amount: BigDecimal,
    val tag: String,
    val issuerAccount: AccountInfo,
    val ownerAccount:  AccountInfo,
    private val participants: List<PublicKey>,
) : ContractState, FungibleState<NumericDecimal> {

    override fun getQuantity(): NumericDecimal {
        return NumericDecimal(amount)
    }

    companion object {
        val tokenType = Token::class.java.name.toString()
    }

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    val issuerHash = issuerAccount.accountHash
    val ownerHash = ownerAccount.accountHash

    override fun toString(): String {
        return  "symbol: $symbol, " +
                "amount: $amount, " +
                "tag: $tag, " +
                "issuerAccount: $issuerAccount, " +
                "issuerHash: $issuerHash, " +
                "ownerAccount: $ownerAccount, " +
                "ownerHash: $ownerHash, " +
                "participants: $participants"
    }

    override fun isFungibleWith(other: FungibleState<NumericDecimal>): Boolean {
        return other is Token && other.issuerHash == issuerHash && other.symbol == symbol
    }
}