package net.corda.plugins.cpk2;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * Internal Gradle {@link Attribute} to preserve a project
 * dependency's original "transitive" setting.
 */
public interface Transitive extends Named {
    Attribute<Transitive> TRANSITIVE_ATTRIBUTE = Attribute.of(Transitive.class);
}
