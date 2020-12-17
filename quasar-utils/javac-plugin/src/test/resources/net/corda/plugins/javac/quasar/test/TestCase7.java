package net.corda.plugins.javac.quasar.test;

import co.paralleluniverse.fibers.Suspendable;

import java.nio.charset.StandardCharsets;

public class TestCase7 {

    @Suspendable
    public void bar() {
        // This should succeed since the method net.corda.plugins.javac.quasar.test.TestCase7#bar is
        // annotated with @Suspendable
        foo(3, "", false, new TestCase7[1], "".getBytes(StandardCharsets.UTF_8));
    }

    //This method is marked suspendable in META-INF/suspendables
    public void foo(int a, String b, boolean c, TestCase7[] arr, byte[] buffer) {}
}