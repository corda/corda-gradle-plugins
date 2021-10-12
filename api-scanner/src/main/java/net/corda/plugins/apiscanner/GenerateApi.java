package net.corda.plugins.apiscanner;

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nonnull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static net.corda.plugins.apiscanner.ApiScanner.GROUP_NAME;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

@SuppressWarnings({"unused", "UnstableApiUsage"})
@DisableCachingByDefault
public class GenerateApi extends DefaultTask {

    private final Property<String> baseName;
    private final Property<String> version;
    private final Provider<RegularFile> target;
    private final ConfigurableFileCollection sources;

    public GenerateApi() {
        setGroup(GROUP_NAME);
        setDescription("Aggregates API scan results found in any sub-projects into a single output.");

        Project project = getProject();
        ObjectFactory objectFactory = project.getObjects();
        baseName = objectFactory.property(String.class).convention("api-" + project.getName());
        version = objectFactory.property(String.class).convention(project.getVersion().toString());

        DirectoryProperty outputDir = objectFactory.directoryProperty().convention(
            project.getLayout().getBuildDirectory().dir("api")
        );
        target = outputDir.file(version.flatMap(v -> baseName.map(n -> createFileName(n, v))));

        sources = project.files(
            project.provider(() ->
                // This will trigger configuration of every ScanApi task in the project.
                project.getAllprojects().stream()
                    .flatMap(p -> p.getTasks()
                        .withType(ScanApi.class)
                        .matching(ScanApi::isEnabled)
                        .stream())
                    .map(ScanApi::getTargets)
                    .collect(toList())
            )
        );
        sources.disallowChanges();
    }

    @Nonnull
    private static String createFileName(String baseName, @Nonnull String version) {
        StringBuilder builder = new StringBuilder(baseName);
        if (!version.isEmpty()) {
            builder.append('-').append(version);
        }
        return builder.append(".txt").toString();
    }

    @Input
    public Property<String> getBaseName() {
        return baseName;
    }

    @Input
    public Property<String> getVersion() {
        return version;
    }

    @PathSensitive(RELATIVE)
    @InputFiles
    public FileCollection getSources() {
        return sources;
    }

    @OutputFile
    public Provider<RegularFile> getTarget() {
        return target;
    }

    @TaskAction
    public void generate() {
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target.get().getAsFile()))) {
            for (File apiFile : getApiFiles()) {
                Files.copy(apiFile.toPath(), output);
            }
        } catch (IOException e) {
            getLogger().error("Failed to generate API file: {}", e.getMessage());
            throw new InvalidUserCodeException(e.getMessage(), e);
        }
    }

    @Nonnull
    private List<File> getApiFiles() {
        List<File> apiFiles = new ArrayList<>(sources.getFiles());
        apiFiles.sort(comparing(File::getName));
        return apiFiles;
    }
}
