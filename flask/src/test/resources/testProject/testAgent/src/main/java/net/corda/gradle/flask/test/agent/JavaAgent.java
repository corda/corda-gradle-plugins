package net.corda.gradle.flask.test.agent;

import java.lang.instrument.Instrumentation;

public class JavaAgent {
    public static boolean running = false;
    public static String agentArgs = null;
    public static void premain(String agentArgs, Instrumentation inst) {
        running = true;
        JavaAgent.agentArgs = agentArgs;
    }
}