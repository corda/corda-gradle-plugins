package net.corda.plugins.javac.quasar.test;

import co.paralleluniverse.fibers.SuspendExecution;

public class TestCase5 {
    public void foo() throws SuspendExecution {}

    public void bar() {
        try {
            foo();
        } catch (SuspendExecution se) {}
    }
}