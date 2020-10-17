package net.corda.gradle;

import net.corda.gradle.jarfilter.DeleteJava;

public class OuterClass {

    @DeleteJava
    public class InnerClass {}

    @DeleteJava
    public static class StaticInnerClass {}

    public class LeftOver {}
}

