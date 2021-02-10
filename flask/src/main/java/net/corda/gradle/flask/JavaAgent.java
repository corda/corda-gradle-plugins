package net.corda.gradle.flask;

import org.gradle.api.Named;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

public class JavaAgent implements Named {
    private final String name;
    private final RegularFileProperty jar;
    private final Property<String> args;

    @Inject
    public JavaAgent(String name, ObjectFactory objects) {
        this.name = name;
        this.jar = objects.fileProperty();
        this.args = objects.property(String.class);
    }

    @Override
    @Nonnull
    @Input
    public String getName() {
        return name;
    }

    @InputFile
    @PathSensitive(RELATIVE)
    public Provider<RegularFile> getJar() {
        return jar;
    }

    public void setJar(Provider<RegularFile> jar) {
        this.jar.set(jar);
    }

    public void setJar(RegularFile jar) {
        this.jar.set(jar);
    }

    @Input
    public Provider<String> getArgs() {
        return args;
    }

    public void setArgs(Provider<String> args) {
        this.args.set(args);
    }

    public void setArgs(String args) {
        this.args.set(args);
    }
}