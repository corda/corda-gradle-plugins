package net.corda.plugins.javac.quasar.test;


import co.paralleluniverse.fibers.SuspendExecution;

public class TestCase15 {

    //This class is mentioned in META-INF/suspendables, this marks all of its methods as suspendables
    private static class Foo {
        public void foo()  {
            System.out.println("Hello world");
        }
    }

    public void bar() {
        Foo foo = new Foo();
        // This should fail since net.corda.plugins.javac.quasar.test.TestCase15#bar
        // is not suspendable
        foo.foo();
    }
}