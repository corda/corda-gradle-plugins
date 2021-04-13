# Gradle Plugins for Corda and Cordapps

The projects at this level of the project are gradle plugins for cordapps and are published to Maven Local with
the rest of the Corda libraries.

From the beginning of development for Corda 4.0 these plugins have been split from the main Corda repository. 
Any changes to gradle plugins pre-4.0 should be on a release branch within the Corda repo. Any changes after 3.0
belong in this repository. 

## Version number

To modify the version number edit the root `build.gradle`.

Until `v4.0`, the version number was tracking the Corda version number it was built for. Eg; Corda `4.0` was built as `4.0.x`.
This was broken from `v4.3` onwards (Corda 4.3 and plugins 5.0). The major version number will change when there is a breaking change,
for example when the minimum (major) version of Gradle changes.

Corda 5 requires Corda Gradle Plugins 6.x.

## Getting started

You will need JVM 8 installed and on the path to run and install these plugins.
However, some plugins may also require a Java 11 toolchain for testing purposes.

## Installing locally

To install locally for testing a new build against Corda you can run the following from the project root;

    ./gradlew install

## Release process

The version number of the "bleeding edge" in `master` is always a `-SNAPSHOT` version. To create a new release, a _maintainer_ must create a new branch off the latest commit in this release, remove the `-SNAPSHOT` from the version number, create a tag and then run the publish to artifactory task for the created tag in Jenkins. The version number in `master` is then advanced to the next `-SNAPSHOT` number.

## Using the plugins.

These plugins are published to R3's [Artifactory](https://software.r3.com/artifactory/corda). More recently, they are also published to Gradle's own plugins
repository and can be imported into your projects using Gradle's `plugins` DSL.

The plugins themselves fall into two categories: those intended for developing CorDapps, and those which are primarily used to build Corda itself.

### Plugins for CorDapp development.

- [`net.corda.plugins.cordapp`](cordapp/README.md)\
This plugin packages your CorDapp classes into a single jar file, along with
the contents of all of the jar's dependent jars. Dependencies which are added
to Gradle's `cordaCompile`, `cordaRuntime` and `cordapp` configurations are
excluded from this packaging. The final jar is then signed. It also provides
a `cordapp` Gradle extension so that you can configure your CorDapp's metadata.
This metadata is currently optional, but you are _strongly_ advised to provide it.

    <sup>Requires Gradle 5.6</sup>

- [`net.corda.plugins.cordformation`](cordformation/README.rst)\
This plugin provides `Cordform`, `Dockerform` and `DockerImage` Gradle tasks
for creating test deployments of multiple Corda nodes and their CorDapps. It
also invokes the Network Bootstrapper over the deployment for you, and provides
you with a `runnodes` script to boot it all up afterwards.

    <sup>Requires Gradle 5.1</sup>

- [`net.corda.plugins.quasar-utils`](quasar-utils/README.rst)\
This plugin configures a Gradle module to use Quasar. Specifically:
    - It allows you to specify the Maven group and version of
the `quasar-core-osgi` artifacts to use.
    - Adds the `quasar-core-osgi` artifact, along with all of its transitive
dependencies, to Gradle's `cordaRuntimeOnly` configuration.
    - Adds the `quasar-core-osgi` artifact to Gradle's `cordaProvided`
configuration without any of its transitive dependencies.
    - Applies the `quasar-core-osgi` Java agent to all of the module's
`JavaExec` tasks.
    - Applies the `quasar-core-osgi` Java agent to all of the module's
`Test` tasks.
    - Provides a `quasar` Gradle extension so that you can configure
which packages the Quasar Java agent should not instrument at runtime.

    <sup>Requires Gradle 6.6</sup>

### Internal Corda plugins.
These plugins are unlikely to be useful to CorDapp developers outside of R3.

- [`net.corda.plugins.api-scanner`](api-scanner/README.md)\
This plugin scans the `public` and `protected` classes inside a Gradle
module's "primary" jar artifact and writes a summary of their `public`
and `protected` methods and fields into an output file. The "primary"
jar is assumed by default to be the one without an `archiveClassifier`,
although this is configurable. Its goal is to alert Corda developers to
accidental breaks in our public ABI for those Corda modules we have
declared to be "stable", and is used by the Continuous Integration builds.

    <sup>Requires Gradle 5.6</sup>

- [`net.corda.plugins.jar-filter`](jar-filter/README.md)\
This plugin allows us to delete certain annotated classes, methods and
fields from the compiled byte-code inside a jar file. It can also rewrite
Kotlin classes' `@kotlin.Metadata` annotations to make them consistent 
again with their revised byte-code. It is currently used in Corda's
`core-deterministic` and `serialization-deterministic` modules, and has
been successfully tested with Kotlin 1.2.x - 1.4.32.

    <sup>Requires Gradle 6.8</sup>

- [`net.corda.plugins.publish-utils`](publish-utils/README.rst)\
**_Here be Dragons!_**
This plugin helps configure Gradle's `maven-publish` plugin to publish Corda
modules to Bintray and to R3's Artifactory. However, its workings probably
shouldn't be allowed and they should _most definitely_ **never** be copied.
Unfortunately, it is heavily constrained by how JFrog's Bintray and Artifactory
plugins themselves work, and so we're currently stuck with it "as is". It _is_
useful though - just shut your eyes and hold your nose.

    <sup>Requires Gradle 6.6</sup>
