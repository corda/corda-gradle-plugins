package net.corda.gradle;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public record RecordWithUnwantedComponentField(
    String name,
    @DeleteField int number,
    long bigNumber
) {
}

@Target(FIELD)
@Retention(RUNTIME)
@interface DeleteField {
}
