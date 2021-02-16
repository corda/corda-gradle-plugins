package net.corda.plugins.javac.quasar.test;


import co.paralleluniverse.fibers.SuspendExecution;

public class TestCase14 {

    private interface Foo {
        // This method is throws SuspendExecution which makes it a SUSPENDABLE_SUPER method,
        // it is not suspendable but it can be overridden by a suspendable method
        abstract void foo() throws SuspendExecution;
    }

    private static class FooImpl implements Foo {
        @Override
        public void foo() {
            System.out.println("Hello world");
        }
    }

    public void bar() {
        FooImpl foo = new FooImpl();
        // This should succeed since net.corda.plugins.javac.quasar.test.TestCase14.FooImpl#foo is not suspendable
        // and only overrides a SUSPENDABLE_SUPER method (not a suspendable one)
        foo.foo();
    }
}