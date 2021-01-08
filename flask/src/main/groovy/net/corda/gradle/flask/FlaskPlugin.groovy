package net.corda.gradle.flask

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import net.corda.flask.common.ManifestEscape
import net.corda.flask.common.Flask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ReadableResource
import org.gradle.api.resources.ResourceException
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar

import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.CharacterIterator
import java.text.StringCharacterIterator

class LauncherResource implements ReadableResource {
    private URL url = getClass().getResource("/META-INF/${getBaseName()}.tar")

    @Override
    InputStream read() throws MissingResourceException, ResourceException {
        return url.openStream()
    }

    @Override
    String getDisplayName() {
        return getBaseName() + ".tar"
    }

    @Override
    URI getURI() {
        return url.toURI()
    }

    @Override
    String getBaseName() {
        return "flask-launcher"
    }

    static final ReadableResource instance = new LauncherResource()
}

class FlaskJarTask extends Jar {

    @Classpath
    @InputFiles
    FileCollection extraClasses

    @Classpath
    @InputFiles
    FileCollection bundledJars

    @Input
    Property<String> launcherClassName

    @Input
    Property<String> mainClassName

    @Input
    ListProperty<String> jvmArgs

    private static class JavaAgent {
        File jar
        String args
    }

    private List<JavaAgent> javaAgents

    @InputFiles
    FileCollection getAgentJars() {
        return project.files(javaAgents.collect {it.jar })
    }

    @Input
    List<String> getAgentArgs() {
        return javaAgents.collect {it.args }
    }

    def javaAgent(File jar, String args) {
        javaAgents += new JavaAgent(jar: jar, args: args)
    }

    @Input
    String launcherArchiveHash() {
        MessageDigest md5 = MessageDigest.getInstance("MD5")
        new DigestInputStream(LauncherResource.instance.read(), md5).withStream { stream ->
            byte[] buffer = new byte[0x10000]
            while(stream.read(buffer) >= 0) {}
        }
        return String.format("%032x", new BigInteger(1, md5.digest()))
    }

    FlaskJarTask() {
        launcherClassName = project.objects.property(String.class).convention("net.corda.flask.launcher.Launcher")
        mainClassName = project.objects.property(String.class)
        jvmArgs = project.objects.listProperty(String.class).convention(new ArrayList<String>())
        javaAgents = new ArrayList<JavaAgent>()
        bundledJars = project.files()
        extraClasses = project.files()
    }

    @Override
    Task configure(Closure closure) {
        FlaskJarTask flaskJarTask = this
        super.configure {
            Object de = delegate
            Object ow = owner
            Object to = thisObject
            closure.rehydrate(de, ow, to)()
            exclude "META-INF/MANIFEST.MF"
            from (project.tarTree(LauncherResource.instance))
            from(extraClasses)
            into ("/LIB-INF") {
                from(bundledJars)
            }
            doFirst {
                manifest { Manifest m ->
                    flaskJarTask.configureManifest(m)
                }
            }
        }
    }

    @CompileStatic
    private static String escapeStringList(List<String> strings){
        StringBuilder sb = new StringBuilder()
        int i = 0
        while(true) {
            CharacterIterator it = new StringCharacterIterator(strings[i])
            for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
                switch (c) {
                    case '"':
                        sb.append("\\\"")
                        break
                    case '\r':
                        sb.append("\\r")
                        break
                    case '\n':
                        sb.append("\\n")
                        break
                    case '\t':
                        sb.append("\\t")
                        break
                    case ' ':
                        sb.append("\\ ")
                        break
                    case '\\':
                        sb.append("\\\\")
                        break
                    default:
                        sb.append(c)
                        break
                }
            }
            if(++i < strings.size()) {
                sb.append(' ')
            } else {
                break
            }
        }
        return sb.toString()
    }

    def configureManifest(Manifest m) {
        m.attributes 'Main-Class': launcherClassName.get()
        mainClassName.getOrNull()?.with {
            m.attributes 'Application-Class': it
        }
        if(!jvmArgs.get().isEmpty()) {
            m.attributes((Flask.ManifestAttributes.JVM_ARGS) : ManifestEscape.escapeStringList(jvmArgs.get()))
        }
        MessageDigest md5 = MessageDigest.getInstance("MD5")
        byte[] buffer = new byte[0x10000]
        if(!javaAgents.isEmpty()) {
            List<String> agentsStrings = javaAgents.collect { javaAgent ->
                md5.reset()
                new DigestInputStream(javaAgent.jar.newInputStream(), md5).withStream { stream ->
                    while (stream.read(buffer) >= 0) {}
                }
                StringBuilder sb = new StringBuilder()
                sb.append(String.format("%032x", new BigInteger(1, md5.digest())))
                if(javaAgent.args) {
                    sb.append('=')
                    sb.append(javaAgent.args)
                }
                sb.toString()
            }
            m.attributes((Flask.ManifestAttributes.JAVA_AGENTS) : ManifestEscape.escapeStringList(agentsStrings))
        }
        bundledJars.collect { file ->
            md5.reset()
            new DigestInputStream(file.newInputStream(), md5).withStream { stream ->
                while (stream.read(buffer) >= 0) {}
            }
            m.attributes(['MD5-Digest': String.format("%032x", new BigInteger(1, md5.digest()))], "/LIB-INF/${file.name}")
        }
    }
}

class FlaskPlugin implements Plugin<Project> {

    @CompileStatic
    static File getCurrentJar() {
        final URL url = FlaskPlugin.class.classLoader.getResource(FlaskPlugin.class.getName().replace('.', '/') + ".class")
        final String path = url.getPath()
        final URI jarUri = new URI(path.substring(0, path.indexOf('!')))
        return Paths.get(jarUri).toFile()
    }

    @Override
    void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        JavaPluginConvention javaPluginConvention = project.convention.getPlugin(JavaPluginConvention.class)
        Provider<SourceSet> flaskSourceSetProvider = javaPluginConvention.sourceSets.register("flask")
        Provider<Copy> extractLauncherTarProvider = project.tasks.register("extractLauncherTar", Copy) {
            destinationDir = project.file('build/classes/flask-launcher')
            from(project.tarTree(LauncherResource.instance))
        }

        project.dependencies {
            add(flaskSourceSetProvider.get().compileOnlyConfigurationName, extractLauncherTarProvider.get().outputs.files)
        }
        Provider<FlaskJarTask> flaskJarTask = project.tasks.register("flaskJar", FlaskJarTask.class) {
            archiveBaseName = "${project.name}-flask"
            Configuration defaultConfiguration = project.configurations["default"]
            TaskOutputs jarTaskOutput = project.tasks.named("jar", Jar).get().outputs
            extraClasses += flaskSourceSetProvider.get().runtimeClasspath
            bundledJars = jarTaskOutput.files + defaultConfiguration

            mainClassName = project.provider {
                String result = project.extensions.findByType(JavaApplication.class)?.mainClassName
                if(!result) throw new GradleException("mainClassName property from \"${JavaApplication.class.name}\" extension is not set")
                return result
            }
        }
        project.extensions.add("flaskJar", flaskJarTask.get())

        project.tasks.create(name: 'flaskRun', type : JavaExec) {
            inputs.files(flaskJarTask.get().outputs)
            classpath(flaskJarTask.get().outputs)
        }
    }
}
