package net.corda.gradle.flask

import net.corda.flask.common.Flask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar

class FlaskPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class)
        Provider<Directory> flaskDir = project.layout.buildDirectory.dir("classes/flask-launcher")
        Provider<Copy> extractLauncherTarProvider = project.tasks.register("extractLauncherTar", Copy) {
            group = Flask.Constants.GRADLE_TASK_GROUP
            description = "Extract the Flask Launcher classes to be used to build a custom launcher"
            into(flaskDir)
            from(project.tarTree(LauncherResource.instance))
        }

        JavaPluginConvention javaPluginConvention = project.convention.getPlugin(JavaPluginConvention.class)
        SourceSet flaskSourceSet = javaPluginConvention.sourceSets.create("flask")
        flaskSourceSet.compiledBy(extractLauncherTarProvider)
        project.configurations.getByName(flaskSourceSet.compileOnlyConfigurationName).withDependencies { dependencies ->
            def launcherDependency = project.dependencies.create(extractLauncherTarProvider.get().outputs.files)
            dependencies.add(launcherDependency)
        }

        Provider<FlaskJarTask> flaskJarTask = project.tasks.register("flaskJar", FlaskJarTask.class) {
            description = "Package the current project code in an executable jar file"
            archiveBaseName = "${project.name}-flask"
            inputs.files(flaskSourceSet.output)
            from {
                flaskSourceSet.runtimeClasspath.collect {
                    if(it.exists()) {
                        it.isDirectory() ? it : project.zipTree(it)
                    } else {
                        null
                    }
                }
            }
            includeLibraries(project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar).map {it.outputs })
            includeLibraries(project.configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME))

            project.pluginManager.withPlugin('application') {
                String result = project.extensions.findByType(JavaApplication.class)?.mainClassName
                if(!result) throw new GradleException("mainClassName property from \"${JavaApplication.class.name}\" extension is not set")
                mainClassName = result
            }
        }
        project.tasks.register('flaskRun', JavaExec) {
            group = Flask.Constants.GRADLE_TASK_GROUP
            description = "Run the jar file created by the 'flaskJar' task"
            inputs.files(flaskJarTask)
            classpath(flaskJarTask)
        }
    }
}
