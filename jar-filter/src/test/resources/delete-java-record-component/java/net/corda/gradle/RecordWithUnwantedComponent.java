package net.corda.gradle;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public record RecordWithUnwantedComponent(
    String name,
    @DeleteComponent int number,
    long bigNumber
) {
}

@Target(RECORD_COMPONENT)
@Retention(RUNTIME)
@interface DeleteComponent {
}
