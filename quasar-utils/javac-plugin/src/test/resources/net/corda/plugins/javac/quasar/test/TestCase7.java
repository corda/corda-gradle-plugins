package net.corda.plugins.javac.quasar.test;

import co.paralleluniverse.fibers.Suspendable;

import java.nio.charset.StandardCharsets;

public class TestCase7 {

    @Suspendable
    public void bar() {
        foo(3, "", false, new TestCase7[1], "".getBytes(StandardCharsets.UTF_8));
    }

    public void foo(int a, String b, boolean c, TestCase7[] arr, byte[] buffer) {}
}