package com.example.contract;

import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.transactions.LedgerTransaction;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class LoggingContract implements Contract {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingContract.class);
    @Override
    public void verify(LedgerTransaction ltx) {
        LOG.info("Transaction: {}", ltx);
    }
}
