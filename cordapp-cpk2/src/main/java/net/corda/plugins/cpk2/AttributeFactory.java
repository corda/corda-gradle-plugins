package net.corda.plugins.cpk2;

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.jetbrains.annotations.NotNull;

import static net.corda.plugins.cpk2.Transitive.TRANSITIVE_ATTRIBUTE;
import static org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE;
import static org.gradle.api.attributes.Bundling.EXTERNAL;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.LibraryElements.JAR;
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;
import static org.gradle.api.attributes.Usage.JAVA_API;
import static org.gradle.api.attributes.Usage.JAVA_RUNTIME;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;

@SuppressWarnings("UnusedReturnValue")
final class AttributeFactory {
    private final AttributeContainer attrs;
    private final ObjectFactory objects;

    AttributeFactory(@NotNull AttributeContainer attrs, @NotNull ObjectFactory objects) {
        this.attrs = attrs;
        this.objects = objects;
    }

    @NotNull
    AttributeFactory javaRuntime() {
        attrs.attribute(USAGE_ATTRIBUTE, objects.named(Usage.class, JAVA_RUNTIME));
        return this;
    }

    @NotNull
    AttributeFactory javaApi() {
        attrs.attribute(USAGE_ATTRIBUTE, objects.named(Usage.class, JAVA_API));
        return this;
    }

    @NotNull
    AttributeFactory asLibrary() {
        attrs.attribute(CATEGORY_ATTRIBUTE, objects.named(Category.class, LIBRARY));
        return this;
    }

    @NotNull
    AttributeFactory jar() {
        attrs.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, JAR));
        return this;
    }

    @NotNull
    AttributeFactory cpb() {
        attrs.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, "cpb"));
        return this;
    }

    @NotNull
    AttributeFactory withExternalDependencies() {
        attrs.attribute(BUNDLING_ATTRIBUTE, objects.named(Bundling.class, EXTERNAL));
        return this;
    }

    @NotNull
    AttributeFactory transitive() {
        attrs.attribute(TRANSITIVE_ATTRIBUTE, objects.named(Transitive.class, "transitive"));
        return this;
    }
}
