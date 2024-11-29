package com.r3.accounts.contracts

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class AccountInfoContract : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
    }
}

interface AccountInfoCommands : Command {
    class Create : AccountInfoCommands
}