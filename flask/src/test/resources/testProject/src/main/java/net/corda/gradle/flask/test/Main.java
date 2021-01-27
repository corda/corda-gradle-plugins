package net.corda.gradle.flask.test;

import net.corda.gradle.flask.test.agent.JavaAgent;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Objects;

class Main {
    public static void main(String[] args) {
        RuntimeMXBean info = ManagementFactory.getRuntimeMXBean();
        int index = info.getInputArguments().indexOf("-Xmx64M");
        if(index < 0) throw new RuntimeException("'-Xmx64M' JVM argument not found");
        String prop = System.getProperty("some.property");
        String expectedPropertyValue = "\"some nasty\nvalue\t\"";
        if(!Objects.equals(expectedPropertyValue, prop)) {
            throw new RuntimeException(String.format("Expected '%s', got '%s'", expectedPropertyValue, prop));
        }

        prop = System.getProperty("another.property");
        expectedPropertyValue = "another nasty\nvalue\t";
        if(!Objects.equals(expectedPropertyValue, prop)) {
            throw new RuntimeException(String.format("Expected '%s', got '%s'", expectedPropertyValue, prop));
        }

        if(!JavaAgent.running) throw new RuntimeException("Java Agent is not running");

        String expectedAgentArgument = "testArgument";
        if(!Objects.equals(expectedAgentArgument, JavaAgent.agentArgs)) {
            throw new RuntimeException(String.format("Expected '%s', got '%s'", expectedAgentArgument, JavaAgent.agentArgs));
        }
    }
}