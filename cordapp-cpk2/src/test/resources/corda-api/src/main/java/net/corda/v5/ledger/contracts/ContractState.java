package net.corda.v5.ledger.contracts;

import java.util.List;
import net.corda.v5.application.identity.AbstractParty;
import org.jetbrains.annotations.NotNull;

public interface ContractState {
    @NotNull
    List<AbstractParty> getParticipants();
}
