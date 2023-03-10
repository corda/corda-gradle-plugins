package net.corda.plugins.cpk2;

import java.io.File;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;

import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_TASK_GROUP;
import static net.corda.plugins.cpk2.CordappUtils.manifestOf;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;
import static org.osgi.framework.Constants.BUNDLE_MANIFESTVERSION;
import static org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.BUNDLE_VERSION;

@DisableCachingByDefault
public class VerifyLibraries extends DefaultTask {
    private final ConfigurableFileCollection _libraries;

    @Inject
    public VerifyLibraries(@NotNull ObjectFactory objects) {
        setDescription("Verifies that a CPK's libraries are all bundles.");
        setGroup(CORDAPP_TASK_GROUP);
        _libraries = objects.fileCollection();
    }

    @PathSensitive(RELATIVE)
    @SkipWhenEmpty
    @InputFiles
    @NotNull
    public FileCollection getLibraries() {
        return _libraries;
    }

    /**
     * Don't eagerly configure the {@link DependencyCalculator} task, even if
     * someone eagerly configures this {@link VerifyLibraries} by accident.
     */
    void setDependenciesFrom(@NotNull TaskProvider<DependencyCalculator> task) {
        _libraries.setFrom(
            /*
             * These jars are the contents of this CPK's lib/ folder.
             */
            task.flatMap(DependencyCalculator::getLibraries)
        );
        _libraries.disallowChanges();
        dependsOn(task);
    }

    @TaskAction
    public void verify() {
        for (File library: _libraries.getFiles()) {
            final Attributes mainAttributes = manifestOf(library).getMainAttributes();
            requireAttribute(mainAttributes, BUNDLE_MANIFESTVERSION, library);
            requireAttribute(mainAttributes, BUNDLE_SYMBOLICNAME, library);
            requireAttribute(mainAttributes, BUNDLE_VERSION, library);
        }
    }

    private void requireAttribute(
        @NotNull Attributes attributes,
        @NotNull String attrName,
        @NotNull File library
    ) {
        if (!attributes.containsKey(new Name(attrName))) {
            getLogger().error("Library {} is not an OSGi bundle. Try declaring it as a 'cordaEmbedded' dependency instead.",
                library.getName());
            throw new InvalidUserDataException("Library " + library.getName() + " has no " + attrName + " attribute");
        }
    }
}
