package net.corda.plugins.cpb2;

import net.corda.plugins.cpk2.Attributor;
import net.corda.plugins.cpk2.CordappExtension;
import net.corda.plugins.cpk2.CordappPlugin;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.NotNull;

import static net.corda.plugins.cpk2.CordappUtils.ALL_CORDAPPS_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.copyJarEnabledTo;
import static net.corda.plugins.cpk2.SignJar.sign;
import static net.corda.plugins.cpk2.SigningProperties.nested;
import static org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION;
import static org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME;

public final class CpbPlugin implements Plugin<Project> {
    private static final String CPB_TASK_NAME = "cpb";
    private static final String CPB_CONFIGURATION_NAME = CPB_TASK_NAME;
    private static final String CORDA_CPB_CONFIGURATION_NAME = "cordaCPB";
    private static final String CPB_PACKAGING_CONFIGURATION_NAME = "cpbPackaging";

    @Override
    public void apply(@NotNull Project project) {
        project.getPluginManager().apply(CordappPlugin.class);
        final Configuration allCordappsConfiguration = project.getConfigurations().getByName(ALL_CORDAPPS_CONFIGURATION_NAME);
        final Attributor attributor = new Attributor(project.getObjects());

        final Configuration cpbConfiguration = project.getConfigurations().create(CPB_CONFIGURATION_NAME)
            .setDescription("Additional CPK dependencies to include inside the CPB.")
            .extendsFrom(allCordappsConfiguration)
            .setVisible(false);
        cpbConfiguration.setCanBeConsumed(false);
        cpbConfiguration.setCanBeResolved(false);

        final Configuration cpbPackaging = project.getConfigurations().create(CPB_PACKAGING_CONFIGURATION_NAME)
            .setTransitive(false)
            .setVisible(false)
            .extendsFrom(cpbConfiguration);
        cpbPackaging.setCanBeConsumed(false);

        project.getConfigurations().create(CORDA_CPB_CONFIGURATION_NAME)
            .attributes(attributor::forCpb)
            .setCanBeResolved(false);

        final TaskProvider<Jar> cpkTask = project.getTasks().named(JAR_TASK_NAME, Jar.class);
        final Provider<RegularFile> cpkPath = cpkTask.flatMap(Jar::getArchiveFile);
        final ConfigurableFileCollection allCPKs = project.getObjects().fileCollection().from(cpkPath, cpbPackaging);
        final TaskProvider<CpbTask> cpbTaskProvider = project.getTasks().register(CPB_TASK_NAME, CpbTask.class, cpbTask -> {
            cpbTask.dependsOn(cpbPackaging.getBuildDependencies());
            cpbTask.from(allCPKs);
            final CordappExtension cordappExtension = project.getExtensions().findByType(CordappExtension.class);
            if (cordappExtension == null) {
                throw new GradleException("cordapp extension not found");
            }
            nested(cpbTask.getInputs(), "cordappSigning", cordappExtension.getSigning());

            // Basic configuration of the CPB task.
            cpbTask.getDestinationDirectory().convention(cpkTask.flatMap(Jar::getDestinationDirectory));
            cpbTask.getArchiveBaseName().convention(cpkTask.flatMap(Jar::getArchiveBaseName));
            cpbTask.getArchiveAppendix().convention(cpkTask.flatMap(Jar::getArchiveAppendix));
            cpbTask.getArchiveVersion().convention(cpkTask.flatMap(Jar::getArchiveVersion));

            cpbTask.doLast(task -> {
                if (cordappExtension.getSigning().getEnabled().get()) {
                    sign(task, cordappExtension.getSigning().getOptions(), cpbTask.getArchiveFile().get().getAsFile());
                }
            });

            // Disable this task if the jar task is disabled.
            project.getGradle().getTaskGraph().whenReady(graph ->
                copyJarEnabledTo(cpbTask).execute(graph)
            );
        });

        final ArtifactHandler artifacts = project.getArtifacts();
        artifacts.add(ARCHIVES_CONFIGURATION, cpbTaskProvider);
        artifacts.add(CORDA_CPB_CONFIGURATION_NAME, cpbTaskProvider);
    }
}
