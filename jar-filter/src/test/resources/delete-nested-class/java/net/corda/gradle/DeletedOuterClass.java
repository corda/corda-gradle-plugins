package net.corda.gradle;

import net.corda.gradle.jarfilter.DeleteJava;

@DeleteJava
public class DeletedOuterClass {
    public class InnerClass {}
}

