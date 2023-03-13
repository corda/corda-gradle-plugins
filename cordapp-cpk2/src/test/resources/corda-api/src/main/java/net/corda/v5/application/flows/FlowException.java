package net.corda.v5.application.flows;

import org.jetbrains.annotations.Nullable;

public class FlowException extends Exception {
    public FlowException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
