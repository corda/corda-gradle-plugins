package net.corda.plugins.javac.quasar.test;

import java.nio.charset.StandardCharsets;

public class TestCase6 {

    public void bar() {
        foo(3, "", false, new TestCase6[1], "".getBytes(StandardCharsets.UTF_8));
    }

    public void foo(int a, String b, boolean c, TestCase6[] arr, byte[] buffer) {}
}