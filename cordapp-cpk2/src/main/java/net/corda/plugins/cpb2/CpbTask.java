package net.corda.plugins.cpb2;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarInputStream;

import static java.util.Collections.singleton;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_TASK_GROUP;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_CPK_TYPE;
import static net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CPK_FILE_EXTENSION;

@DisableCachingByDefault
public class CpbTask extends Jar {
    private static final String CPB_ARTIFACT_CLASSIFIER = "package";
    public static final String CPB_FILE_EXTENSION = "cpb";
    private static final String CPK_FILE_SUFFIX = '.' + CPK_FILE_EXTENSION;
    private static final Set<String> EXCLUDED_CPK_TYPES = singleton("corda-api");
    public static final String CPB_NAME_ATTRIBUTE = "Corda-CPB-Name";
    public static final String CPB_VERSION_ATTRIBUTE = "Corda-CPB-Version";
    public static final String CPB_FORMAT_VERSION = "Corda-CPB-Format";
    public static final String CPB_CURRENT_FORMAT_VERSION = "2.0";

    public CpbTask() {
        setGroup(CORDAPP_TASK_GROUP);
        setDescription("Assembles a .cpb archive that contains the current project's .cpk artifact " +
                "and all of its dependencies");
        getArchiveClassifier().set(CPB_ARTIFACT_CLASSIFIER);
        getArchiveExtension().set(CPB_FILE_EXTENSION);
        setDirMode(Integer.parseInt("555", 8));
        setDuplicatesStrategy(DuplicatesStrategy.FAIL);
        setFileMode(Integer.parseInt("444", 8));
        setEntryCompression(ZipEntryCompression.STORED);
        setManifestContentCharset("UTF-8");
        setMetadataCharset("UTF-8");
        setIncludeEmptyDirs(false);
        setCaseSensitive(true);
        setPreserveFileTimestamps(false);
        setReproducibleFileOrder(true);
        setZip64(true);

        manifest(m -> {
            m.getAttributes().put(CPB_FORMAT_VERSION, CPB_CURRENT_FORMAT_VERSION);
            m.getAttributes().put(CPB_NAME_ATTRIBUTE, getArchiveBaseName());
            m.getAttributes().put(CPB_VERSION_ATTRIBUTE, getArchiveVersion());
        });
    }

    @Override
    @NotNull
    public AbstractCopyTask from(@NotNull Object... args) {
        return super.from(args, copySpec ->
            copySpec.exclude(this::isCPK)
        );
    }

    private boolean isCPK(@NotNull FileTreeElement element) {
        if (!element.getName().endsWith(CPK_FILE_SUFFIX)) {
            return false;
        }

        Path cpkPath = element.getFile().toPath();
        try (JarInputStream cpkStream = new JarInputStream(new BufferedInputStream(Files.newInputStream(cpkPath)))) {
            String cpkType = cpkStream.getManifest().getMainAttributes().getValue(CORDA_CPK_TYPE);
            return cpkType != null && EXCLUDED_CPK_TYPES.contains(cpkType.toLowerCase());
        } catch (IOException e) {
            throw new InvalidUserDataException(e.getMessage(), e);
        }
    }

    public void checkForDuplicates() {
        Set<String> cpkNames = new HashSet<>();
        System.out.println("checking duplicates");
        FileCollection files = getInputs().getFiles();
        for (File file : files) {
            Path path = file.toPath();
            if (path.toString().endsWith(CPK_FILE_SUFFIX)) {
                try (JarInputStream cpkStream = new JarInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                    String cpkCordappName = cpkStream.getManifest().getMainAttributes().getValue(CPK_CORDAPP_NAME);
                    if (cpkCordappName != null) {
                        if (cpkNames.contains(cpkCordappName)) {
                            throw new InvalidUserDataException("Two CPKs may not share a cordappCpkName. Error in " + cpkCordappName);
                        } else {
                            cpkNames.add(cpkCordappName);
                        }
                    }
                } catch (IOException e) {
                    throw new InvalidUserDataException(e.getMessage(), e);
                }
            }
        }
    }
}
