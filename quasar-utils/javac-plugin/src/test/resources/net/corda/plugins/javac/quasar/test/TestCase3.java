package net.corda.plugins.javac.quasar.test;

import java.util.function.Consumer;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.SuspendExecution;

public class TestCase3 {
    @Suspendable
    public void foo() {}
    public void bar() throws SuspendExecution {
        foo();
    }
}