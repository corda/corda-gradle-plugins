package net.corda.gradle;

import net.corda.gradle.jarfilter.DeleteJava;

public record RecordWithFunction(String name, int number, long bigNumber) {
    @DeleteJava
    public String getMessage() {
        return String.format("name:[%s], number:[%d]. bigNumber:[%ld]", name, number, bigNumber);
    }
}
