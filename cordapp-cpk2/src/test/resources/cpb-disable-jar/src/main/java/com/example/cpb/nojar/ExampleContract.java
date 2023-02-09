package com.example.cpb.nojar;

import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class ExampleContract implements Contract {
    @Override
    public void verify(@NotNull LedgerTransaction ltx) {
    }
}

