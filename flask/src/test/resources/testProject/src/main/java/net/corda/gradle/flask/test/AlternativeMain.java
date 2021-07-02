package net.corda.gradle.flask.test;

import java.util.Optional;

import net.corda.gradle.flask.test.agent.JavaAgent;

public class AlternativeMain {
    public static void main(String[] args) {
        String key = "java.agent.supposed.to.be.active";
        boolean javaAgentSupposedtoBeActive = Optional.ofNullable(System.getProperty(key))
                .map(Boolean::parseBoolean).orElseThrow(
                        () -> new AssertionError(String.format("'%s' Java system property is not set", key)));
        if(javaAgentSupposedtoBeActive && !JavaAgent.running) {
            throw new AssertionError("Java Agent is not running while it should");
        } else if(!javaAgentSupposedtoBeActive && JavaAgent.running) {
            throw new AssertionError("Java Agent is running while it shoudn't");
        }
    }
}