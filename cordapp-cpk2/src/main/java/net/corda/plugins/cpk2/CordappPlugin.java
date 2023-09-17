package net.corda.plugins.cpk2;

import aQute.bnd.gradle.BndBuilderPlugin;
import aQute.bnd.gradle.BundleTaskExtension;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static aQute.bnd.osgi.Constants.FIXUPMESSAGES;
import static aQute.bnd.osgi.Constants.NOEXTRAHEADERS;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableList;
import static net.corda.plugins.cpk2.Attributor.isPlatformModule;
import static net.corda.plugins.cpk2.CordappProperties.nested;
import static net.corda.plugins.cpk2.CordappUtils.ALL_CORDAPPS_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CLASSES_IN_WRONG_DIRECTORY_FIXUP;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONFIG_PLUGIN_ID;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONTRACT_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONTRACT_VERSION;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_DOCUMENTATION_URL;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_EXTERNAL_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_PACKAGING_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_PLATFORM_VERSION;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_WORKFLOW_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_WORKFLOW_VERSION;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_ALL_PROVIDED_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_CPK_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_EMBEDDED_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_PRIVATE_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_PROVIDED_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_RUNTIME_ONLY_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_LICENCE;
import static net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_VENDOR;
import static net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_VERSION;
import static net.corda.plugins.cpk2.CordappUtils.CPK_FORMAT;
import static net.corda.plugins.cpk2.CordappUtils.CPK_FORMAT_TAG;
import static net.corda.plugins.cpk2.CordappUtils.CPK_PLATFORM_VERSION;
import static net.corda.plugins.cpk2.CordappUtils.PLATFORM_VERSION_X;
import static net.corda.plugins.cpk2.CordappUtils.createBasicConfiguration;
import static net.corda.plugins.cpk2.CordappUtils.createCompileConfiguration;
import static net.corda.plugins.cpk2.CordappUtils.filterIsInstance;
import static net.corda.plugins.cpk2.CordappUtils.map;
import static net.corda.plugins.cpk2.CordappUtils.mapTo;
import static net.corda.plugins.cpk2.CordappUtils.maxOf;
import static net.corda.plugins.cpk2.CordappUtils.parseInstruction;
import static net.corda.plugins.cpk2.CordappUtils.parseInstructions;
import static net.corda.plugins.cpk2.CordappUtils.setCannotBeDeclared;
import static org.gradle.api.file.DuplicatesStrategy.FAIL;
import static org.gradle.api.plugins.JavaBasePlugin.UNPUBLISHABLE_VARIANT_ARTIFACTS;
import static org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME;
import static org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED;
import static org.osgi.framework.Constants.BUNDLE_LICENSE;
import static org.osgi.framework.Constants.BUNDLE_NAME;
import static org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.BUNDLE_VENDOR;
import static org.osgi.framework.Constants.BUNDLE_VERSION;

/**
 * Generate a new CPK format CorDapp for use in Corda.
 */
@SuppressWarnings("unused")
public final class CordappPlugin implements Plugin<Project> {
    private static final int UNKNOWN_PLATFORM_VERSION = -1;
    private static final String EXCLUDE_EXTRA_HEADERS = NOEXTRAHEADERS + "=true";
    private static final String CORDA_PLATFORM_VERSION = "Corda-Platform-Version";
    private static final String PLUGIN_PROPERTIES = "cordapp-cpk2.properties";
    private static final String DEPENDENCY_CALCULATOR_TASK_NAME = "cordappDependencyCalculator";
    private static final String CPK_DEPENDENCIES_TASK_NAME = "cordappCPKDependencies";
    private static final String VERIFY_LIBRARIES_TASK_NAME = "verifyLibraries";
    private static final String VERIFY_BUNDLE_TASK_NAME = "verifyBundle";
    private static final String CORDAPP_COMPONENT_NAME = "cordapp";
    private static final String CORDAPP_EXTENSION_NAME = "cordapp";
    private static final String OSGI_EXTENSION_NAME = "osgi";
    private static final String MIN_GRADLE_VERSION = "7.2";
    private static final String UNKNOWN = "Unknown";
    private static final int MINIMUM_PLATFORM = 1;
    private static final int TARGET_PLATFORM = 0;

    private static final String CORDAPP_VERSION_INSTRUCTION = CPK_CORDAPP_VERSION + "=${" + BUNDLE_VERSION + '}';
    private static final String CLASSES_IN_WRONG_DIRECTORY_WARNING_INSTRUCTION = FIXUPMESSAGES + '=' + CLASSES_IN_WRONG_DIRECTORY_FIXUP;

    private static final List<String> CORDAPP_BUILD_CONFIGURATIONS = unmodifiableList(asList(
        /*
         * Every CorDapp configuration is a super-configuration of at least one of these
         * configurations. Hence every {@link org.gradle.api.artifacts.ProjectDependency}
         * needed to build this CorDapp should exist somewhere beneath their umbrella.
         */
        CORDAPP_PACKAGING_CONFIGURATION_NAME,
        CORDAPP_EXTERNAL_CONFIGURATION_NAME
    ));

    @NotNull
    private static Properties getPluginProperties() {
        final Properties props = new Properties();
        try (final InputStream input = CordappPlugin.class.getResourceAsStream(PLUGIN_PROPERTIES)) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props;
    }

    @NotNull
    private static String getValue(@NotNull Properties props, @NotNull String name) {
        final String value = props.getProperty(name);
        if (value == null) {
            throw new InvalidUserCodeException(name + " missing from CPK plugin");
        }
        return value;
    }

    private final ProjectLayout layouts;
    private final SoftwareComponentFactory softwareComponentFactory;

    /**
     * CorDapp's information.
     */
    private CordappExtension cordapp;

    @Inject
    public CordappPlugin(@NotNull ProjectLayout layouts, @NotNull SoftwareComponentFactory softwareComponentFactory) {
        this.layouts = layouts;
        this.softwareComponentFactory = softwareComponentFactory;
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public void apply(@NotNull Project project) {
        project.getLogger().info("Configuring {} as a CorDapp", project.getName());

        if (GradleVersion.current().compareTo(GradleVersion.version(MIN_GRADLE_VERSION)) < 0) {
            throw new GradleException("Gradle version " + GradleVersion.current().getVersion()
                + " is below the supported minimum version " + MIN_GRADLE_VERSION
                + ". Please update Gradle or consider using Gradle wrapper if it is provided with the project. "
                + "More information about CorDapp build system can be found here: " + CORDAPP_DOCUMENTATION_URL);
        }

        {
            final PluginManager pluginManager = project.getPluginManager();
            // Apply the 'java-library' plugin on the assumption that we're building a JAR.
            // This will also create the "api", "implementation", "compileOnly" and "runtimeOnly" configurations.
            pluginManager.apply("java-library");

            // Apply the Bnd "builder" plugin to generate OSGi metadata for the CorDapp.
            pluginManager.apply(BndBuilderPlugin.class);
        }

        {
            final Properties pluginProperties = getPluginProperties();
            // Create our plugin's "cordapp" extension.
            cordapp = project.getExtensions().create(CORDAPP_EXTENSION_NAME, CordappExtension.class,
                getValue(pluginProperties, "osgiVersion"),
                getValue(pluginProperties, "bndVersion"),
                getValue(pluginProperties, "jetbrainsAnnotationsVersion")
            );
        }

        final ConfigurationContainer configurations = project.getConfigurations();
        // Generator object for variant attributes. We need this to ensure
        // Gradle resolves project dependencies into the correct artifacts.
        final Attributor attributor = new Attributor(project.getObjects());

        // Create the outgoing configuration, and add it to custom software component.
        final Configuration cordaCPK = configurations.create(CORDA_CPK_CONFIGURATION_NAME)
            .attributes(attributor::forJar);
        cordaCPK.setCanBeResolved(false);
        setCannotBeDeclared(cordaCPK);

        final AdhocComponentWithVariants component = softwareComponentFactory.adhoc(CORDAPP_COMPONENT_NAME);
        component.addVariantsFromConfiguration(configurations.getByName(API_ELEMENTS_CONFIGURATION_NAME),
            new CordappVariantMapping("compile")
        );
        component.addVariantsFromConfiguration(configurations.getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME),
            new CordappVariantMapping("runtime")
        );
        component.addVariantsFromConfiguration(cordaCPK, cvd -> {});
        project.getComponents().add(component);

        // This definition of cordaRuntimeOnly must be kept aligned with the one in the quasar-utils plugin.
        final Configuration cordaRuntimeOnly = createBasicConfiguration(configurations, CORDA_RUNTIME_ONLY_CONFIGURATION_NAME)
            .setDescription("Runtime-only dependencies which do not belong to this CorDapp.")
            .setTransitive(false);
        configurations.getByName(RUNTIME_ONLY_CONFIGURATION_NAME).extendsFrom(cordaRuntimeOnly);

        configurations.getByName(COMPILE_ONLY_CONFIGURATION_NAME).withDependencies(dependencies -> {
            final DependencyHandler dependencyHandler = project.getDependencies();
            final Dependency bndDependency = dependencyHandler.create("biz.aQute.bnd:biz.aQute.bnd.annotation:" + cordapp.getBndVersion().get());
            dependencies.add(bndDependency);

            final Dependency osgiDependency = dependencyHandler.create("org.osgi:osgi.annotation:" + cordapp.getOsgiVersion().get());
            dependencies.add(osgiDependency);

            final Dependency jetbrainsDependency = dependencyHandler.create("org.jetbrains:annotations:" + cordapp.getJetbrainsAnnotationsVersion().get());
            dependencies.add(jetbrainsDependency);
        });

        // We will ALWAYS want to compile against bundles, and not classes.
        // Bnd probably sets this attribute already, but still set it anyway.
        configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME).attributes(attributor::forJar);

        final Configuration cordappCfg = createCompileConfiguration(configurations, CORDAPP_CONFIGURATION_NAME)
            .setDescription("The CorDapps that this CorDapp directly depends on.");

        // The "cordapp" and "cordaProvided" configurations are "compile only",
        // which causes their dependencies to be excluded from the published
        // POM. This also means that CPK dependencies will not be transitive
        // by default, and so we must implement a way of fixing this ourselves.
        final CordappDependencyCollector collector = new CordappDependencyCollector(configurations, project.getDependencies(), attributor, project.getLogger());
        final Configuration allCordapps = createCompileConfiguration(configurations, ALL_CORDAPPS_CONFIGURATION_NAME)
            .setDescription("Every CorDapp this CorDapp depends on, either directly or indirectly.")
            .extendsFrom(cordappCfg)
            .withDependencies(dependencies -> {
                collector.collect();
                dependencies.addAll(collector.getCordappDependencies());
                filterIsInstance(dependencies, ModuleDependency.class).forEach(dep -> {
                    if (isPlatformModule(dep)) {
                        return;
                    }

                    // Ensure that none of these dependencies is transitive. This will prevent
                    // Gradle from adding any of these CorDapps' private library dependencies
                    // to our own compile classpath.
                    // WE ARE MUTATING THESE DEPENDENCIES FOR EVERY CONFIGURATION THEY APPEAR IN!
                    if (dep.isTransitive()) {
                        // Synchronised to be sure! I don't know how multithreaded Gradle is.
                        synchronized(dep) {
                            if (dep instanceof ProjectDependency) {
                                // Preserve the original setting so that
                                // CordappDependencyCollector can use it.
                                dep.attributes(attributor::forTransitive);
                            }
                            dep.setTransitive(false);
                        }
                    }

                    // We also need to GUARANTEE that Gradle uses the jar artifact here.
                    // Only the jar contains the OSGi metadata we need.
                    if (dep instanceof ProjectDependency) {
                        dep.attributes(attributor::forJar);
                    }
                });
            });

            // This definition of cordaProvided must be kept aligned with the one in the quasar-utils plugin.
            final Configuration cordaProvided = createCompileConfiguration(configurations, CORDA_PROVIDED_CONFIGURATION_NAME)
                .setDescription("Compile-only dependencies which Corda will provide at runtime.");

            // Unlike cordaProvided dependencies, cordaPrivateProvided ones will not be
            // added to the compilation classpath of any CPKs that will depend on this CPK.
            // In other words, they will not be included in this CPK's marker POM.
            final Configuration cordaPrivate = createCompileConfiguration(configurations, CORDA_PRIVATE_CONFIGURATION_NAME)
                .setDescription("Corda-provided dependencies which are only available to this CorDapp.");

            final Configuration allProvided = createCompileConfiguration(configurations, CORDA_ALL_PROVIDED_CONFIGURATION_NAME)
                .setDescription("Every Corda-provided dependency, including private and transitive ones.")
                .extendsFrom(cordaProvided, cordaPrivate)
                .withDependencies(dependencies -> {
                    collector.collect();
                    dependencies.addAll(collector.getProvidedDependencies());
                });

            // Embedded dependencies should not appear in the CorDapp's published POM.
            final Configuration cordaEmbedded = createCompileConfiguration(configurations, CORDA_EMBEDDED_CONFIGURATION_NAME)
                .setDescription("These dependencies are added to the CorDapp's Bundle-Classpath.");

            // We need to resolve the contents of our CPK file based on
            // both the runtimeElements and cordaEmbedded configurations.
            // This won't happen by default because cordaEmbedded is a
            // "compile only" configuration.
            final Configuration cordappPackaging = configurations.create(CORDAPP_PACKAGING_CONFIGURATION_NAME)
                .setVisible(false)
                .extendsFrom(cordaEmbedded, configurations.getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME))
                .attributes(attributor::forRuntimeClasspath);
            cordappPackaging.setCanBeConsumed(false);
            setCannotBeDeclared(cordappPackaging);

            final Configuration cordappExternal = configurations.create(CORDAPP_EXTERNAL_CONFIGURATION_NAME)
                .setVisible(false)
                .extendsFrom(allProvided, allCordapps)
                .attributes(attributor::forCompileClasspath);
            cordappExternal.setCanBeConsumed(false);
            setCannotBeDeclared(cordappExternal);

        // We need to perform some extra work on the root project to support publication.
        project.getPluginManager().withPlugin("maven-publish", new CordappPublishing(project.getRootProject()));

        configureCordappTasks(project);
    }

    /**
     * Configures this project's JAR as a CorDapp JAR. Ensure that the
     * JAR is reproducible, i.e. that its hash is stable!
     */
    private void configureCordappTasks(@NotNull Project project) {
        final ConfigurationContainer configurations = project.getConfigurations();
        final TaskProvider<DependencyCalculator> calculatorTask = project.getTasks().register(DEPENDENCY_CALCULATOR_TASK_NAME, DependencyCalculator.class, task ->
            task.setDependsOn(
                /*
                 * Every CorDapp configuration is a super-configuration of at least one of these
                 * configurations. Hence every {@link ProjectDependency} needed to build this
                 * CorDapp should exist somewhere beneath their umbrella.
                 */
                mapTo(
                    new LinkedHashSet<>(),
                    map(CORDAPP_BUILD_CONFIGURATIONS, configurations::getByName),
                    Configuration::getBuildDependencies
                )
            )
        );

        /*
         * Generate an extra resource file listing this CorDapp's CPK dependencies.
         */
        final Provider<Directory> cpkDependenciesDir = layouts.getBuildDirectory().dir("cpk-dependencies");
        final TaskProvider<CPKDependenciesTask> cpkDependenciesTask = project.getTasks().register(CPK_DEPENDENCIES_TASK_NAME, CPKDependenciesTask.class, task -> {
            task.setCPKsFrom(calculatorTask);
            task.getOutputDir().set(cpkDependenciesDir);
            task.getHashAlgorithm().set(cordapp.getHashAlgorithm());
        });

        final SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME, main ->
            main.getOutput().dir(singletonMap("builtBy", cpkDependenciesTask), cpkDependenciesDir)
        );

        final ObjectFactory objects = project.getObjects();
        final TaskProvider<Jar> jarTask = project.getTasks().named(JAR_TASK_NAME, Jar.class, jar -> {
            nested(jar.getInputs(), CORDAPP_EXTENSION_NAME, cordapp);

            // Prepare list of libraries
            final ConfigurableFileCollection libraries = objects.fileCollection();
            libraries.setFrom(calculatorTask.flatMap(DependencyCalculator::getLibraries));
            libraries.disallowChanges();

            final OsgiExtension osgi = jar.getExtensions().create(OSGI_EXTENSION_NAME, OsgiExtension.class, objects, jar);
            osgi.embed(calculatorTask.flatMap(DependencyCalculator::getEmbeddedJars));
            OsgiProperties.nested(jar.getInputs(), OSGI_EXTENSION_NAME, osgi);

            final BundleTaskExtension bndBundle = jar.getExtensions().getByType(BundleTaskExtension.class);
            // Add jars which have been migrated off the Bundle-Classpath
            // back into Bnd's regular classpath.
            bndBundle.classpath(calculatorTask.flatMap(DependencyCalculator::getUnbundledJars));

            // Add a Bnd instruction to export the set of observed package names.
            bndBundle.bnd(osgi.getExports());

            // Add Bnd instructions to embed requested jars into this bundle.
            bndBundle.bnd(osgi.getEmbeddedJars());

            // Add a Bnd instruction for explicit package imports.
            bndBundle.bnd(osgi.getImports());

            // Add Bnd instructions to scan for any contracts, flows, schemas etc.
            bndBundle.bnd(osgi.getScanCordaClasses());

            // Set the CPK version tag to the same value as Bundle-Version.
            bndBundle.bnd(CORDAPP_VERSION_INSTRUCTION);

            // Instruct Bnd not to add any extra manifest headers concerning JDK or Bnd versions.
            bndBundle.bnd(EXCLUDE_EXTRA_HEADERS);

            // Instruct Bnd that finding META-INF/versions/ classes is not an error.
            bndBundle.bnd(CLASSES_IN_WRONG_DIRECTORY_WARNING_INSTRUCTION);

            // Disable the bndfile property, which could clobber our bnd instructions.
            bndBundle.getBndfile().fileValue(null).disallowChanges();

            // Accessing the Gradle [Project] during the task execution
            // phase is incompatible with Gradle's configuration cache.
            // Prevent this task from accessing the project's properties.
            bndBundle.getProperties().convention(emptyMap());

            final ConfigurableFileCollection allCordaProvided = objects.fileCollection()
                .from(calculatorTask.flatMap(DependencyCalculator::getProvidedJars));
            final ConfigurableFileCollection allCordapps = objects.fileCollection().from(
                calculatorTask.flatMap(DependencyCalculator::getRemoteCordapps),
                calculatorTask.flatMap(DependencyCalculator::getProjectCordapps)
            );
            // Add libraries
            jar.from(libraries, libs ->
                libs.into("/META-INF/privatelib/")
            );
            jar.doFirst(t -> {
                if (!osgi.getConfigured()) {
                    t.getLogger().warn("CORDAPP PLUGIN NOT CONFIGURED! Please apply '{}' plugin to root project.", CORDAPP_CONFIG_PLUGIN_ID);
                }

                // Check that Bnd still contains all the instructions we expect.
                final Map<String, String> instructions = parseInstructions(bndBundle.getBnd().get());
                // No need to check the exports because they're computed as the jar is built.
                mustContain(instructions, osgi.getImports().get());
                mustContainAll(instructions, parseInstructions(osgi.getEmbeddedJars().get()));
                mustContainAll(instructions, parseInstructions(osgi.getScanCordaClasses().get()));
                mustContain(instructions, CORDAPP_VERSION_INSTRUCTION);
                mustContain(instructions, EXCLUDE_EXTRA_HEADERS);

                final Jar jt = (Jar) t;
                jt.setFileMode(Integer.parseInt("444", 8));
                jt.setDirMode(Integer.parseInt("555", 8));
                jt.setManifestContentCharset("UTF-8");
                jt.setPreserveFileTimestamps(false);
                jt.setReproducibleFileOrder(true);
                jt.setEntryCompression(DEFLATED);
                jt.setDuplicatesStrategy(FAIL);
                jt.setIncludeEmptyDirs(false);
                jt.setCaseSensitive(true);
                jt.setZip64(true);

                final Attributes attributes = jt.getManifest().getAttributes();
                // check whether metadata has been configured (not mandatory for non-flow, non-contract gradle build files)
                if (cordapp.getContract().isEmpty() && cordapp.getWorkflow().isEmpty()) {
                    throw new InvalidUserDataException("CorDapp metadata not defined for this Gradle build file. See " + CORDAPP_DOCUMENTATION_URL);
                }

                // Compute the maximum platform version used by any "corda-provided"
                // dependencies, or by any CorDapp dependencies.
                final int platformVersion = Math.max(
                    maxOf(allCordaProvided, CORDA_PLATFORM_VERSION, UNKNOWN_PLATFORM_VERSION),
                    maxOf(allCordapps, CORDAPP_PLATFORM_VERSION, UNKNOWN_PLATFORM_VERSION)
                );
                if (platformVersion > UNKNOWN_PLATFORM_VERSION) {
                    attributes.put(CORDAPP_PLATFORM_VERSION, platformVersion);
                    attributes.put(CPK_PLATFORM_VERSION, platformVersion);
                }

                configureCordappAttributes(osgi.getSymbolicName().get(), attributes);
            }).doLast(t -> {
                final Signing signing = cordapp.getSigning();
                if (signing.getEnabled().get()) {
                    SignJar.sign(t, signing.getOptions(), ((Jar) t).getArchiveFile().get().getAsFile());
                } else {
                    t.getLogger().lifecycle("CorDapp JAR signing is disabled, the CorDapp's contracts will not use signature constraints.");
                }
            });
        });

        /*
         * Check that all of this CPK's libraries are actually bundles.
         */
        final TaskProvider<VerifyLibraries> verifyLibraries = project.getTasks().register(VERIFY_LIBRARIES_TASK_NAME, VerifyLibraries.class, verify ->
            verify.setDependenciesFrom(calculatorTask)
        );

        /*
         * Ask Bnd to "sanity-check" this new bundle.
         */
        final TaskProvider<VerifyBundle> verifyBundle = project.getTasks().register(VERIFY_BUNDLE_TASK_NAME, VerifyBundle.class, verify -> {
            verify.getBundle().set(jarTask.flatMap(Jar::getArchiveFile));
            verify.setDependenciesFrom(calculatorTask);

            // Disable this task if the jar task is disabled.
            project.getGradle().getTaskGraph().whenReady(CordappUtils.copyJarEnabledTo(verify));
        });
        jarTask.configure(jar -> {
            jar.dependsOn(verifyLibraries);
            jar.finalizedBy(verifyBundle);
        });

        project.getArtifacts().add(CORDA_CPK_CONFIGURATION_NAME, jarTask);
    }

    private void configureCordappAttributes(@NotNull String symbolicName, @NotNull Attributes attributes) {
        attributes.put(BUNDLE_SYMBOLICNAME, symbolicName);
        attributes.put(CPK_CORDAPP_NAME, symbolicName);
        attributes.put(CPK_FORMAT_TAG, CPK_FORMAT);

        final CordappData contract = cordapp.getContract();
        if (!contract.isEmpty()) {
            final String contractName = contract.getName().getOrElse(symbolicName);
            final String vendor = contract.getVendor().getOrElse(UNKNOWN);
            final String licence = contract.getLicence().getOrElse(UNKNOWN);
            attributes.put(BUNDLE_NAME, contractName);
            attributes.put(BUNDLE_VENDOR, vendor);
            attributes.put(BUNDLE_LICENSE, licence);
            attributes.put(CORDAPP_CONTRACT_NAME, contractName);
            attributes.put(CORDAPP_CONTRACT_VERSION, checkCorDappVersionId(contract.getVersionId()));
            attributes.put("Cordapp-Contract-Vendor", vendor);
            attributes.put("Cordapp-Contract-Licence", licence);
        }
        final CordappData workflow = cordapp.getWorkflow();
        if (!workflow.isEmpty()) {
            final String workflowName = workflow.getName().getOrElse(symbolicName);
            final String vendor = workflow.getVendor().getOrElse(UNKNOWN);
            final String licence = workflow.getLicence().getOrElse(UNKNOWN);
            attributes.put(BUNDLE_NAME, workflowName);
            attributes.put(BUNDLE_VENDOR, vendor);
            attributes.put(BUNDLE_LICENSE, licence);
            attributes.put(CORDAPP_WORKFLOW_NAME, workflowName);
            attributes.put(CORDAPP_WORKFLOW_VERSION, checkCorDappVersionId(workflow.getVersionId()));
            attributes.put("Cordapp-Workflow-Vendor", vendor);
            attributes.put("Cordapp-Workflow-Licence", licence);
        }
        attributes.put(CPK_CORDAPP_VENDOR, attributes.get(BUNDLE_VENDOR));
        attributes.put(CPK_CORDAPP_LICENCE, attributes.get(BUNDLE_LICENSE));

        final int[] platformVersion = checkPlatformVersionInfo();
        attributes.put("Target-Platform-Version", platformVersion[TARGET_PLATFORM]);
        attributes.put("Min-Platform-Version", platformVersion[MINIMUM_PLATFORM]);
        if (cordapp.getSealing().get()) {
            attributes.put("Sealed", true);
        }
    }

    @NotNull
    private int[] checkPlatformVersionInfo() {
        // If the minimum platform version is not set, it defaults to X.
        final int minimumPlatformVersion = cordapp.getMinimumPlatformVersion().get();
        final int targetPlatformVersion = cordapp.getTargetPlatformVersion().get();
        if (targetPlatformVersion < PLATFORM_VERSION_X) {
            throw new InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than " + PLATFORM_VERSION_X + '.');
        }
        if (targetPlatformVersion < minimumPlatformVersion) {
            throw new InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than the `minimumPlatformVersion` (" + minimumPlatformVersion + ')');
        }
        return new int[] { targetPlatformVersion, minimumPlatformVersion };
    }

    private static int checkCorDappVersionId(@NotNull Property<Integer> versionId) {
        if (!versionId.isPresent()) {
            throw new InvalidUserDataException("CorDapp `versionId` was not specified in the associated `contract` or `workflow` metadata section. Please specify a whole number starting from 1.");
        }
        final int value = versionId.get();
        if (value < 1) {
            throw new InvalidUserDataException("CorDapp `versionId` must not be smaller than 1.");
        }
        return value;
    }

    private static void mustContain(@NotNull Map<String, String> instructions, @Nullable String item) {
        if (item == null) {
            return;
        }
        final String[] required = parseInstruction(item);
        if (required == null) {
            return;
        }
        final String actualValue = instructions.get(required[0]);
        if (!Objects.equals(actualValue, required[1])) {
            throw new InvalidUserDataException("Bnd instruction '" + item + "' was replaced with '" + actualValue + "'");
        }
    }

    private static void mustContainAll(@NotNull Map<String, String> instructions, @NotNull Map<String, String> items) {
        if (!items.isEmpty()) {
            final Map<String, String> missing = new LinkedHashMap<>(items);
            items.keySet().forEach(key ->
                missing.remove(key, instructions.get(key))
            );
            if (!missing.isEmpty()) {
                final Map<String, String> replacements = new LinkedHashMap<>(instructions);
                replacements.keySet().retainAll(missing.keySet());
                throw new InvalidUserDataException("Bnd instructions " + missing + " were replaced with " + replacements + '.');
            }
        }
    }
}

final class CordappVariantMapping implements Action<ConfigurationVariantDetails> {
    private final String mavenScope;

    CordappVariantMapping(@NotNull String mavenScope) {
        this.mavenScope = mavenScope;
    }

    @Override
    public void execute(@NotNull ConfigurationVariantDetails variant) {
        final PublishArtifactSet artifacts = variant.getConfigurationVariant().getArtifacts();
        if (artifacts.stream().anyMatch(artifact -> UNPUBLISHABLE_VARIANT_ARTIFACTS.contains(artifact.getType()))) {
            variant.skip();
        }
        variant.mapToMavenScope(mavenScope);
    }
}
