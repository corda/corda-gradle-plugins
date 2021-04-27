package net.corda.gradle;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@RemoveFromRecord
public record RecordWithUnwantedAnnotation(@RemoveFromRecord int number) {
}

@Target({TYPE, RECORD_COMPONENT})
@Retention(RUNTIME)
@interface RemoveFromRecord {
}
