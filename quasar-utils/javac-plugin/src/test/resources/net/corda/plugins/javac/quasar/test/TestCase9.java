package net.corda.plugins.javac.quasar.test;


public class TestCase9 {

    private static abstract class Foo {
        //This method is marked suspendable in META-INF/suspendables
        abstract void foo();
    }

    private static class FooImpl extends Foo {
        @Override
        public void foo() {
            System.out.println("Hello world");
        }
    }

    public void bar() {
        FooImpl foo = new FooImpl();
        // This should fail since the method net.corda.plugins.javac.quasar.test.TestCase9.FooImpl#foo
        // overrides the suspendable method net.corda.plugins.javac.quasar.test.TestCase9.Foo#foo
        foo.foo();
    }
}