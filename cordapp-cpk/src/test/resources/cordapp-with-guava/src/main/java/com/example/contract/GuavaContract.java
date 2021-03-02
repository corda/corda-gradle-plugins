package com.example.contract;

import com.google.common.collect.ImmutableList;
import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.transactions.LedgerTransaction;

import java.util.List;

public class GuavaContract implements Contract {
    @Override
    public void verify(LedgerTransaction ltx) {
        List<String> data = ImmutableList.of("one", "two", "three");
        for (String item : data) {
            System.out.println(item);
        }
    }
}
