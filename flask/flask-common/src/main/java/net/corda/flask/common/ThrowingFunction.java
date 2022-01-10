package net.corda.flask.common;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {
    @Override
    default R apply(T value) {
        try {
            return applyThrowing(value);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    R applyThrowing(T value) throws Exception;
}
