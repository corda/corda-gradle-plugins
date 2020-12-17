package net.corda.plugins.javac.quasar.test;


public class TestCase10 {

    private interface Foo {
        //This method is marked suspendable in META-INF/suspendables
        abstract void foo();
    }

    private static class FooImpl implements Foo {
        @Override
        public void foo() {
            System.out.println("Hello world");
        }
    }

    public void bar() {
        FooImpl foo = new FooImpl();
        // This should fail since the method net.corda.plugins.javac.quasar.test.TestCase10.FooImpl#foo
        // overrides the suspendable method net.corda.plugins.javac.quasar.test.TestCase10.Foo#foo
        foo.foo();
    }
}