package net.corda.gradle.jarfilter;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Target({TYPE, CONSTRUCTOR, METHOD, FIELD, PACKAGE})
@Retention(RUNTIME)
public @interface DeleteJava {
}

