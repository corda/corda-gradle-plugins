package net.corda.plugins.javac.quasar.test;

import co.paralleluniverse.fibers.SuspendExecution;

public class TestCase5 {
    public void foo() throws SuspendExecution {}

    public void bar() {
        try {
            //This should fail since net.corda.plugins.javac.quasar.test.TestCase5#bar is not suspendable
            foo();
        } catch (SuspendExecution se) {}
    }
}