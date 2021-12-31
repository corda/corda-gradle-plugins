package net.corda.flask.common;

import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {
    @Override
    default void accept(T value) {
        try {
            acceptThrowing(value);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    void acceptThrowing(T value) throws Exception;
}
