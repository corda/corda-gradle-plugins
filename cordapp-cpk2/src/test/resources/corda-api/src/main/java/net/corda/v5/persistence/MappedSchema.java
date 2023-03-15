package net.corda.v5.persistence;

import org.jetbrains.annotations.NotNull;

public class MappedSchema {
    private final Class<?> schemaFamily;
    private final int version;
    private final Iterable<Class<?>> mappedTypes;

    public MappedSchema(@NotNull Class<?> schemaFamily, int version, @NotNull Iterable<Class<?>> mappedTypes) {
        this.schemaFamily = schemaFamily;
        this.version = version;
        this.mappedTypes = mappedTypes;
    }

    @NotNull
    public final String getName() {
        return schemaFamily.getName();
    }

    public final int getVersion() {
        return version;
    }

    @NotNull
    public final Iterable<Class<?>> getMappedTypes() {
        return mappedTypes;
    }
}
