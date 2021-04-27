package net.corda.gradle;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public record RecordWithUnwantedComponentMethod(
    String name,
    @DeleteMethod int number,
    long bigNumber
) {
}

@Target(METHOD)
@Retention(RUNTIME)
@interface DeleteMethod {
}
