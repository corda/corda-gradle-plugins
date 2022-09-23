package com.example.importpackage;

import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.transactions.LedgerTransaction;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class ExampleContract implements Contract {
    private final Logger logger = LoggerFactory.getLogger(ExampleContract.class);

    @Override
    public void verify(LedgerTransaction ltx) {
        logger.info("Verify transaction: {}", ltx);
    }
}
