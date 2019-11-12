package net.corda.example;

import java.util.concurrent.Future;

public interface ExtendedInterface<T> extends Future<T>, Comparable<T>, Appendable {
    String getName();
    void setName(String name);
}
