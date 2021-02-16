package net.corda.plugins.javac.quasar.test;

public class TestCase8 {

    public void bar() {
        //This should fail as the method net.corda.plugins.javac.quasar.test.TestCase8#foo is suspendable
        foo(null);
    }

    //This method is marked suspendable in META-INF/suspendables
    public void foo(Void v) {}
}