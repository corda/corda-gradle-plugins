package net.corda.plugins.javac.quasar.test;


import co.paralleluniverse.fibers.Suspendable;

public class TestCase11 {

    private interface Foo {
        //This method is marked suspendable in META-INF/suspendables
        abstract void foo();
    }

    private static class FooImpl implements Foo {
        @Override
        public void foo() {}
    }

    private static class FooImplImpl extends FooImpl {
        @Override
        public void foo() {
            System.out.println("Hello world");
        }
    }

    public void bar() {
        Foo foo = new FooImplImpl();
        // This should fail since the method 'foo' of interface net.corda.plugins.javac.quasar.test.TestCase11.Foo
        // is a suspendable method
        foo.foo();
    }
}