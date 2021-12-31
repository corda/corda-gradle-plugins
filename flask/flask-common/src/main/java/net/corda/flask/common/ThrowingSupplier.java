package net.corda.flask.common;

import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T> extends Supplier<T> {
    @Override
    default T get() {
        try {
            return getThrowing();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    T getThrowing() throws Exception;
}
