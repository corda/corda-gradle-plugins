package net.corda.plugins.javac.quasar.test;

import java.util.function.Consumer;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.SuspendExecution;

public class TestCase1 {
    public void foo() {}
    public void bar() {
        // This should succeed as net.corda.plugins.javac.quasar.test.TestCase1#foo is not suspendable
        foo();
    }
}