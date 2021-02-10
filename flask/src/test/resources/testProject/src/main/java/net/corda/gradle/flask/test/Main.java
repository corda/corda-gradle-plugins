package net.corda.gradle.flask.test;

import net.corda.gradle.flask.test.agent.JavaAgent;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.lang.IllegalArgumentException;

class Main {

    private static String arr2String(String[] array) {
        return Arrays.stream(array).map(s -> "\"" + s + "\"").collect(Collectors.joining(", "));
    }

    private static void assertEquals(Object expectedObject, Object actualObject) {
        if(!Objects.equals(expectedObject, actualObject))
            throw new AssertionError(String.format("Expected '%s', got '%s'", expectedObject, actualObject));
    }

    public static void main(String[] args) {
        String[] expectedCliArgs = new String [] {"arg1", "arg2", "arg3"};
        if(!Arrays.equals(expectedCliArgs, args)) {
            throw new IllegalArgumentException(String.format("Received arguments [%s] differs from expected arguments [%s]",
                    arr2String(args), arr2String(expectedCliArgs)));
        }
        RuntimeMXBean info = ManagementFactory.getRuntimeMXBean();
        int index = info.getInputArguments().indexOf("-Xmx64M");
        if(index < 0) throw new IllegalArgumentException("'-Xmx64M' JVM argument not found");
        String prop = System.getProperty("some.property");
        String expectedPropertyValue = "\"some nasty\nvalue\t\"";
        assertEquals(expectedPropertyValue, prop);

        prop = System.getProperty("another.property");
        expectedPropertyValue = "another nasty\nvalue\t";
        assertEquals(expectedPropertyValue, prop);

        prop = System.getProperty("some.property.from.cli");
        expectedPropertyValue = "some value from cli";
        assertEquals(expectedPropertyValue, prop);

        prop = System.getProperty("property.to.be.overridden");
        expectedPropertyValue = "value from cli";
        assertEquals(expectedPropertyValue, prop);

        if(!JavaAgent.running) throw new AssertionError("Java Agent is not running");

        String expectedAgentArgument = "testArgument";
        assertEquals(expectedAgentArgument, JavaAgent.agentArgs);
    }
}