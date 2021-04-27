package net.corda.gradle;

import net.corda.gradle.jarfilter.DeleteJava;

@DeleteJava
public abstract sealed class DeletableSealedClass permits SealedSubclass {
}
