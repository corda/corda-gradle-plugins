package net.corda.plugins.javac.quasar.test;

import java.nio.charset.StandardCharsets;

public class TestCase6 {

    public void bar() {
        // This should fail since the method net.corda.plugins.javac.quasar.test.TestCase6#bar is not suspendable
        foo(3, "", false, new TestCase6[1], "".getBytes(StandardCharsets.UTF_8));
    }

    //This method is marked suspendable in META-INF/suspendables
    public void foo(int a, String b, boolean c, TestCase6[] arr, byte[] buffer) {}
}