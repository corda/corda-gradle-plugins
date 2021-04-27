package net.corda.gradle;

import net.corda.gradle.jarfilter.DeleteJava;

public record RecordWithConstructor(String name, int number, long bigNumber) {
    @DeleteJava
    public RecordWithConstructor() {
        this("<default-value>", 0, -1L);
    }
}
