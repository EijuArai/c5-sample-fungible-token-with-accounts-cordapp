package com.r3.accounts

import com.r3.accounts.states.AccountInfo
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefTransformer
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderFactory

class AccountInfoQueryFactory : VaultNamedQueryFactory {
    override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
        vaultNamedQueryBuilderFactory.create("ACCOUNT_INFO_QUERY")
            .whereJson(
                "WHERE visible_states.custom_representation -> 'com.r3.accounts.states.AccountInfo' " +
                        "->> 'identifier' = :identifier"
            )
            .map(AccountInfoQueryTransformer())
            .register()
    }
}

class AccountInfoQueryTransformer : VaultNamedQueryStateAndRefTransformer<AccountInfo, AccountInfo> {
    override fun transform(data: StateAndRef<AccountInfo>, parameters: MutableMap<String, Any>): AccountInfo {
        return data.state.contractState
    }
}