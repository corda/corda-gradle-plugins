package net.corda.gradle;

import net.corda.gradle.jarfilter.DeleteJava;

@DeleteJava
public final class UnwantedSubclass extends WantedSealedClass {
}
