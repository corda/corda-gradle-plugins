package net.corda.gradle.flask


import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskOutputs
import org.gradle.jvm.tasks.Jar

class FlaskPlugin implements Plugin<Project> {

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
            inputs.files(flaskSourceSetProvider.map {it.output })
            from {
                flaskSourceSetProvider.get().runtimeClasspath.collect {
                    if(it.exists()) {
                        it.isDirectory() ? it : project.zipTree(it)
                    } else {
                        null
                    }
                }
            }
            TaskOutputs jarTaskOutput = project.tasks.named("jar", Jar).get().outputs
            includeLibraries(jarTaskOutput.files + defaultConfiguration)

            project.pluginManager.withPlugin('application') {
                String result = project.extensions.findByType(JavaApplication.class)?.mainClassName
                if(!result) throw new GradleException("mainClassName property from \"${JavaApplication.class.name}\" extension is not set")
                mainClassName = result
            }
        }
        project.extensions.add("flaskJar", flaskJarTask.get())

        Provider<JavaExec> flaskRunTask = project.tasks.register('flaskRun', JavaExec) {
            inputs.files(flaskJarTask.get().outputs)
            classpath(flaskJarTask.get().outputs)
        }
        project.extensions.add("flaskRun", flaskRunTask.get())
    }
}
