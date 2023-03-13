package net.corda.v5.application.flows;

@FunctionalInterface
public interface Flow<T> {
    T call() throws FlowException;
}
