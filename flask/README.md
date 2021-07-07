## Overview
Flask is a Gradle plugin that allows you to package a Java application in a single executable jar file,
along with all of its dependencies and the required JVM arguments. To do so, it creates an executable jar
that creates a boostrap JVM that extracts all the required Java dependencies to a temporary directory and
composes the Java command line and spawns the actual JVM process as a child process.

This plugin adds 2 Gradle tasks to you project:

- the `flaskJar` task that assembles the project executable jar 
- the `flaskRun` task that launches the executable jar created by the `flaskJar` task in a new JVM process

## Quick start

Just apply the plugin to you Gradle Java project

```groovy
plugins {
    id 'net.corda.plugins.flask' version "$flaskVersion"
}
```

and add a small closure to configure the name of the main class
```groovy
flaskJar {
    mainClassName = 'main.class.Name'
}
```

finally run

```bash
./gradlew flaskJar
```
in your project directory to assemble the executable jar in the `build/libs` folder

## Usage

This plugin creates two new Gradle tasks in your project:

- the `flaskJar` task that assembles the project executable jar
- the `flaskRun` task that launches the executable jar created by the `flaskJar` task in a new JVM process

it also registers these task as Gradle extension objects under the names `flaskJar` and `flaskRun` so that they 
can be easily configured.

#### Provide JVM arguments from cli 
It is possible to specify jvm arguments and Java agents that will be applied by default when starting the application.

Additional JVM arguments can be added to the child process launching the generated jar with
```bash
java -jar flask.jar -flaskJvmArg="-Xmx4G" -flaskJvmArg="-Dsome.property=\"some value\""
```

#### Override child process main class
It is possible to override the name of the child process main class with

```bash
java -Dnet.corda.flask.main.class="new.main.class.Name" -jar flask.jar
```

#### Disable included Java agents
You can disable the Java agents incldued in the generated jar file with

```bash
java -Dnet.corda.flask.no.java.agent="true" -jar flask.jar
```

### The *flaskJar* task

This task, which is of type `net.corda.gradle.flask.FlaskJarTask` (that extends `org.gradle.api.tasks.bundling.AbstractArchiveTask`), 
creates the executable jar file.
It supports all that [its parent task provides](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/bundling/AbstractArchiveTask.html),
with the addition of the ability to set the main class name, the list of jvm parameters and java agents.
The project main `flaskJar` task (that packages the current project main artifact and its *runtimeClasspath* configuration)
can be easily configured using the groovy extension object named `flaskJar`:

```groovy
flaskJar {
    mainClassName = 'main.class.Name'
    jvmArgs = ["-Xmx8G", "-Dsome.property=\"some value\""]
    javaAgent {
        testAgent {
            jar = project.file("agent.jar")
            args = "agentArguments"
        }
    }
}
```

The default destination directory for the generated artifact is `build/libs` and the default artifact name is
```groovy
"${project.name}-flask-${project.version}.jar"
```
(note that this can be customized using the properties of `org.gradle.api.tasks.bundling.AbstractArchiveTask`)

### The *flaskRun* task

This task, which is of type `org.gradle.api.tasks.JavaExec`, runs the executable jar file created by the *flaskJar* task,
it can be configured with the *flaskRun* extension object

```groovy
flaskRun {
    workingDir = project.buildDir
}
```

### Advanced usage

It is also possible to dynamically edit the java command line from java code itself (the generated executable runs
a boot JVM that spawn the actual JVM running the main class as a subprocess). 

This is done configuring `net.corda.gradle.flask.FlaskJarTask.launcherClassName` with the name of the class that
will be used to run the bootstrap JVM. It is mandatory for that class to extend `net.corda.flask.launcher.Launcher`.

It is then possible to override the methods `beforeChildJvmStart` and `afterChildJvmExit`, the first one is invoked 
before creation of the child JVM process and is passed a (mutable) instance of `net.corda.flask.launcher.JavaProcessBuilder` 
that can be used to edit the process command line at will, the second is called after the child process terminates and
receives its exit code as a parameter. 

```java
public class customLauncher extends Launcher {
    @Override
    protected void beforeChildJvmStart(JavaProcessBuilder builder) {
        ...
    }

    @Override
    protected void afterChildJvmExit(int returnCode) {
        ...
    }
}
```

## How does it work internally

The resulting jar artifact contains your project main artifact along with all its resolved `runtimeClasspath` dependencies
embedded as stored zip entries in the `/LIB-INF` folder, additionally it contains the following metadata 
in the jar manifest main attribute:

- `Application-Class` contains the name of the main class of the child process
  
And 2 properties files in the `META-INF` folder:

- `jvmArgs.xml` contains the jvm argument list it is supposed to use to spawn the child process
- `javaAgents.xml` contains the list of hashes of the java agents jars and their arguments

When the executable jar is started, the method `net.corda.flask.launcher.Launcher.main` is invoked. 
It uses a cache directory, whose location is platform dependent and that is shared between all **Flask** processes, 
to avoid extracting the library dependencies at every process launch.
That cache directory contains an empty lockfile. 
The bootstrap JVM process tries to acquire an exclusive (write) lock on that file and, should it succeed, 
it deletes all the files whose last modification date is older than a predetermined threshold 
(which currently stands at 7 days), then it releases the lock and re-acquires it as a shared (read) lock,
it reads all the manifest entries in the `LIB-INF` folder and extracts them in the cache directory 
with the path `lib/$fileHash/$fileName` only if they don't already exist (this way the cache can never
contain two identical jar files). At this point the bootstrap process creates another empty lockfile in the cache 
directory `pid` subfolder (the heartbeat lock) and acquires an exclusive lock on it, then it extracts from its own jar the application metadata 
(main class name, JVM argument list and Java agents), builds the command line and spawns a subprocess adding its own jar 
as a Java agent of its child; this java agent in the child process simply starts a thread that acquires a shared
lock on the heartbeat lock and calls `System.exit(-1)` while holding it.

This ensures the child process suicides as soon as the parent process releases the heartbeat lock 
(that only happens if the parent crashes or is forcefully terminated while waiting for the child to exit, 
otherwise the parent process only releases the heartbeat lock after the child has terminated).

## Logging
The injected launcher code internally uses `slf4j-simple`, which means that debug logging can be enabled with

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=trace -jar flask.jar
```

