package net.corda.plugins.javac.quasar.test;

import java.util.function.Consumer;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.SuspendExecution;

public class TestCase4 {
    @Suspendable
    public void foo() {}
    public void bar() throws SuspendExecution {
        // This should succeed since net.corda.plugins.javac.quasar.test.TestCase4#bar
        // is marked as suspendable throwing SuspendExecution
        foo();
    }

    public void bar4() {
        Consumer<Void> c = new Consumer<Void>() {
            @Override
            public void accept(Void arg) {
                //This should fail since java.util.function.Consumer.accept is not suspendable
                foo();
            }
        };
    }
}