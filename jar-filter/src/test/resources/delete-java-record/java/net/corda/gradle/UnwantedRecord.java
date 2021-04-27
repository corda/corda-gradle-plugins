package net.corda.gradle;

import net.corda.gradle.jarfilter.DeleteJava;

@DeleteJava
public record UnwantedRecord(String name, int value, long longValue) {
}
