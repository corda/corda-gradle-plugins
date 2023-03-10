package net.corda.plugins.cpk2;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.model.ObjectFactory;
import org.jetbrains.annotations.NotNull;

import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.ENFORCED_PLATFORM;
import static org.gradle.api.attributes.Category.REGULAR_PLATFORM;

/**
 * Generator for Gradle {@link Configuration} variant attributes.
 */
public final class Attributor {
    private final ObjectFactory objects;

    /**
     * Identify a platform dependency by examining its {@link Category} variant attribute.
     * @param dependency A Gradle {@link ModuleDependency}.
     * @return true if this is a platform module, otherwise false.
     */
    public static boolean isPlatformModule(@NotNull ModuleDependency dependency) {
        final Category category = dependency.getAttributes().getAttribute(CATEGORY_ATTRIBUTE);
        if (category == null) {
            return false;
        }
        final String categoryName = category.getName();
        return REGULAR_PLATFORM.equals(categoryName) || ENFORCED_PLATFORM.equals(categoryName);
    }

    public Attributor(@NotNull ObjectFactory objects) {
        this.objects = objects;
    }

    /**
     * Dark Gradle Magic which ensures that a configuration
     * is resolved exactly like compileClasspath.
     * @param attrs Receiver for compileClasspath attributes
     */
    public void forCompileClasspath(@NotNull AttributeContainer attrs) {
        new AttributeFactory(attrs, objects)
            .withExternalDependencies()
            .asLibrary()
            .javaApi()
            .jar();
    }

    /**
     * Dark Gradle Magic which ensures that a configuration
     * is resolved exactly like runtimeClasspath.
     * @param attrs Receiver for runtimeClasspath attributes.
     */
    public void forRuntimeClasspath(@NotNull AttributeContainer attrs) {
        new AttributeFactory(attrs, objects)
            .withExternalDependencies()
            .javaRuntime()
            .asLibrary()
            .jar();
    }

    /**
     * Dark Gradle Magic which ensures that we use a
     * project's jar artifact and not just its classes.
     * @param attrs Receiver for jar attribute.
     */
    public void forJar(@NotNull AttributeContainer attrs) {
        new AttributeFactory(attrs, objects).jar();
    }

    /**
     * Dark Gradle Magic to declare that we
     * consume or produce a CPB artifact.
     * @param attrs Receiver for cpb attribute.
     */
    public void forCpb(@NotNull AttributeContainer attrs) {
        new AttributeFactory(attrs, objects).cpb();
    }

    /**
     * Darker Gradle Magic so that we can remember
     * that a dependency was declared transitive.
     * @param attrs Receiver for transitive attribute.
     */
    public void forTransitive(@NotNull AttributeContainer attrs) {
        new AttributeFactory(attrs, objects).transitive();
    }
}
