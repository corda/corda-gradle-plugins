package net.corda.v5.ledger.contracts;

import net.corda.v5.ledger.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface Contract {
    void verify(@NotNull LedgerTransaction ltx);
}
