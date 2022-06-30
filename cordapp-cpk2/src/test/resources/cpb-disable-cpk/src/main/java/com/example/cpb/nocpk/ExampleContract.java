package com.example.cpb.nocpk;

import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.transactions.LedgerTransaction;

public class ExampleContract implements Contract {
    @Override
    public void verify(LedgerTransaction ltx) {
    }
}

