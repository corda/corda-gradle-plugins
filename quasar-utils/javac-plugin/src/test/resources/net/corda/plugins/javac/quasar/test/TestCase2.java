package net.corda.plugins.javac.quasar.test;

import java.util.function.Consumer;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.SuspendExecution;

public class TestCase2 {
    @Suspendable
    public void foo() {}
    public void bar() {
        //This should fail as net.corda.plugins.javac.quasar.test.TestCase2#bar is not suspendable
        foo();
    }
}