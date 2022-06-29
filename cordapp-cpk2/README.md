# Cordapp CPK Gradle Plugin.
<sup>Requires Gradle 6.7</sup>

## Purpose.
Applying this plugin to a project declares that the project should create a CPK-format CorDapp. The CPK-format
CorDapp is a ZIP file with a `.cpk` extension that contains the output of the `jar` task (the "main" jar), along
with that jar's dependent jars. In practice, the plugin will not include any of Corda's own jars among these
dependencies, nor any jars which should be provided by Corda, e.g. Kotlin or Quasar. The "main" jar  should also
contain sufficient OSGi metadata to be a valid OSGi bundle.

## Usage.
Include these lines at the top of the `build.gradle` file of your CorDapp Gradle project:

```gradle
plugins {
    id 'net.corda.plugins.cordapp-cpk2'
}
```

You must also apply the `net.corda.cordapp.cordapp-configuration` plugin to your "root" Gradle project,
to configure the `cordapp-cpk` plugin for your version of Corda:
```gradle
plugins {
    id 'net.corda.cordapp.cordapp-configuration'
}
```

If your Gradle project only contains a single module then you would apply both plugins together:
```gradle
plugins {
    id 'net.corda.cordapp.cordapp-configuration'
    id 'net.corda.plugins.cordapp-cpk2'
}
```

You will also need to declare both plugins' versions in `settings.gradle`:
```gradle
pluginManagement {
    plugins {
        id 'net.corda.cordapp.cordapp-configuration' version cordaReleaseVersion
        id 'net.corda.plugins.cordapp-cpk2' version cpkPluginVersion
    }
}
```
where `cpkPluginVersion` and `cordaReleaseVersion` are both Gradle properties:
```gradle
cpkPluginVersion = '6.0.0'
cordaReleaseVersion = '5.0.0'
```

Applying the `cordapp-cpk` plugin implicitly applies both Gradle's `java-library` plugin and Bnd's `builder` plugin,
which means that the output of the `jar` task will also become an OSGi bundle. The `cordapp-cpk` plugin assigns
the following default OSGi attributes to the bundle:

- `Bundle-SymbolicName`: `${project.group}.${archiveBaseName}[-${archiveAppendix}][-${archiveClassifier}]`
- `Bundle-Version`: `${project.version}`

### DSL.

The plugin creates a `cordapp` DSL extension, which currently bears a strong resemblance to the
legacy `cordapp` plugin's DSL extension:
```groovy
cordapp {
    targetPlatformVersion = '<Corda 5 Platform Version>'
    minimumPlatformVersion = '<Corda 5 Platform Version>'

    contract {
        name = 'Example CorDapp'
        versionId = 1
        licence = 'Test-Licence'
        vendor = 'R3'
    }

    workflow {
        name = 'Example CorDapp'
        versionId = 1
        licence = 'Test-Licence'
        vendor = 'R3'
    }

    signing {
        enabled = (true | false)

        // These options presumably mirror Ant's signJar task options.
        options {
            alias = '??'
            storePassword = '??'
            keyStore = file('/path/to/keystore')
            storeType= ('PKCS12' | 'JKS')
            keyPassword = '$storePassword'
            signatureFileName = '$alias'
            verbose = (true | false)
            strict = (true | false)
            internalSF = (true | false)
            sectionsOnly = (true | false)
            lazy = (true | false)
            maxMemory = '??'
            preserveLastModified = (true | false)
            tsaUrl = '??'
            tsaCert = '??'
            tsaProxyHost = '??'
            tsaProxyPort = '??'
            executable = file('/path/to/alternate/jarsigner')
            force = (true | false)
            signatureAlgorithm = '??'
            digestAlgorithm = '??'
            tsaDigestAlgorithm = '??'
        }
    }

    sealing = (true | false)

    hashAlgorithm = 'SHA-256'
}
```

This extension is likely to change as Corda 5 matures and its requirements evolve. The `name`,
`licence` and `vendor` fields are mapped to the `Bundle-Name`, `Bundle-License` and `Bundle-Vendor`
OSGi manifest tags respectively.

### Configurations.

Applying `cordapp-cpk` creates the following new Gradle configurations:

- `cordaProvided`: This configuration declares a dependency that the CorDapp needs to compile against,
but which should not become part of the CPK since it will be provided by the Corda node at runtime.
This effectively replaces the legacy `cordaCompile` configuration. Any `cordaProvided` dependency
is also implicitly added to Gradle's `compileOnly` and `*Implementation` configurations, and consequently
is not included either in the `runtimeClasspath` configuration, as a dependency in the published POM
file, or packaged inside the CPK file. However, it will be included in the `testRuntimeClasspath`
configuration, and will also become a transitive `cordaProvided` dependency of any CorDapp which depends
on this CorDapp.

- `cordaPrivateProvided`: This configuration is like `cordaProvided`, except that its contents do
not become transitive `cordaProvided` dependencies of any CorDapps which depend on this one.

- `cordapp`: This declares a compile-time dependency against the "main" jar of another CPK CorDapp.
As with `cordaProvided`, the dependency is also added implicitly to Gradle's `compileOnly` and
`*Implementation` configurations, and is excluded from the `runtimeClasspath` configuration, the
published POM file, and the contents of the CPK file. The "main" jars of all `cordapp` dependencies
are listed as lines in this "main" jar's `META-INF/CPKDependencies` file:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cpkDependencies xmlns="corda-cpk">
    <cpkDependency>
        <name>$BUNDLE_SYMBOLIC_NAME</name>
        <version>$BUNDLE_VERSION</version>
        <signers>
            <signer algorithm="$HASH_ALGORITHM">$BASE64_HASH_OF_PUBLIC_KEY</signer>
        </signers>
    </cpkDependency>
    ...
</cpkDependencies>
```
`cordapp` dependencies are transitive in the sense that if CorDapp `B` declares a `cordapp`
dependency on CorDapp `A`, and then CorDapp `C` declares a `cordapp` dependency on CorDapp `B`,
then CorDapp `C` will acquire compile-time dependencies on the "main" jars of both CorDapps `A`
and `B`. The `cordaProvided` dependencies of both `A` and `B` will also be added to CorDapp `C`'s
`cordaProvided` configuration. This piece of _Dark Magic_ is achieved by publishing each CPK with
a "companion" POM that contains the extra dependency information. The `cordapp-cpk` plugin resolves
these "companion" POMs transparently to the user so that CorDapps have the transitive relationships
that everyone expects.

Note that in order for everything to work as intended, the "companion" POM must be published into
the same repository as its associated "main" jar artifact. For a jar with Maven coordinates:
```
    ${group}:${artifact}:${version}
```
the "companion"'s Maven coordinates will be:
```
    ${group}.${artifact}:${artifact}.corda.cpk:${version}
```

- `cordaEmbedded`: This configuration behaves similarly to `cordaProvided` in the sense that it
declares a `compileOnly` dependency that is excluded from both the CPK contents and from the
published POM. The difference is that the dependent jar is also added to a `lib/` folder inside the
CorDapp's "main" jar, and appended to the jar's `Bundle-Classpath` manifest attribute. Note that
an OSGi framework considers a `Bundle-Classpath` to contain ordinary jars and not bundles, even
if those jars contain OSGi metadata of their own. Note also that the embedded jars' transitive
dependencies will be embedded too, unless they are explicitly added to another Gradle configuration.
_Use embedding with care!_ It is provided as a tool for those cases where a dependency has no OSGi
metadata, or its metadata is somehow unusable. I strongly recommend _not_ embedding dependencies
which already have valid OSGi metadata.

- `cordaRuntimeOnly`: This declares a dependency that will be added to the `runtimeClasspath`, but
which the CorDapp doesn't need to compile against and must not be packaged into the CPK file either.
It replaces the legacy `cordaRuntime` configuration.

The legacy `cordaCompile` and `cordaRuntime` configurations are built upon Gradle's deprecated
`compile` and `runtime` configurations, which have finally been removed in Gradle 7.0.

# Publishing

The `cordapp-cpk` plugin creates a new Gradle `SoftwareComponent` named "cordapp", which you
can use to create a `MavenPublication`:
```groovy
plugins {
    id 'net.corda.plugins.cordapp-cpk2'
    id 'maven-publish'
}

publishing {
    publications {
        myCorDapp(MavenPublication) {
            from components.cordapp
        }
    }
}
```

# Tasks.

## External Tasks.

- `jar`: This is the standard `Jar` task created by Gradle's `java-library` plugin, and
then enhanced by Bnd's `builder` plugin to create an OSGi bundle.

- `cpk`: This task creates a `.cpk` file with the output from `jar` as its "main" jar. The
contents of the jar's `runtimeClasspath` configuration is added to the CPK's `lib/` folder,
except for those jars which have been declared as either a `cordapp`, `cordaProvided`,
`cordaEmbedded` or `cordaRuntimeOnly` dependency.

The `jar` and `cpk` tasks are both automatic dependencies of Gradle's `assemble` task.

## Internal Tasks.

These tasks perform intermediate steps as part of creating a CPK.

- `cordappDependencyCalculator`: Calculates which jars belong to which part of a CPK's packaging.

- `cordappDependencyConstraints`: Generates the "main" jar's `META-INF/DependencyConstraints` file.

- `cordappCPKDependencies`: Generates the "main" jar's `META-INF/CPKDependencies` file.

- `verifyBundle`: Verifies that the "main" jar's OSGi metadata is consistent with the packages
that have been included in the CPK. This task uses Bnd's `Verifier` class with "strict" verification
enabled to ensure that every `Import-Package` element has an associated version too.

# OSGi Metadata.

The `cordapp-cpk` plugin automatically adds these dependencies to the CorDapp:
```groovy
compileOnly "biz.aQute.bnd:biz.aQute.bnd.annotation:$bndVersion"
compileOnly "org.osgi:osgi.annotation:8.1.0"
```

These annotations [control how Bnd will generate OSGi metadata](https://bnd.bndtools.org/chapters/230-manifest-annotations.html)
for the "main" jar. In practice, the plugin already tries to handle the typical cases for creating CorDapps.

## Package Exports

The `cordapp-cpk` plugin creates a Bnd `-exportcontents` command to generate the "main" jar's OSGi
`Export-Package` header. By default, it will automatically add every package inside the "main" jar to this
`-exportcontents` command. The assumption here is that a CorDapp will not have a complicated package structure,
and that Corda's OSGi sandboxes will provide additional CorDapp isolation anyway.

CorDapp developers who wish to configure their package exports more precisely can disable this default behaviour
from the `jar` task:
```groovy
tasks.named('jar', Jar) {
    osgi {
        autoExport = false
    }
}
```
You can then apply `@org.osgi.annotation.bundle.Export` annotations "by hand" to selected `package-info.java`
files.

You can also export package names explicitly, although applying `@Export` annotations would still be better:
```groovy
tasks.named('jar', Jar) {
    osgi {
        exportPackage 'com.example.cordapp', 'com.example.cordapp.foo'
    }
}
```

## Package Imports

In an ideal world, Bnd would generate the correct OSGi `Import-Package` manifest header automatically. That being
said, Bnd will occasionally also notice unexpected package references from unused code-paths within the byte-code.
The `cordapp-cpk` plugin provides the following options to override the detected package settings:
```groovy
tasks.named('jar', Jar) {
    osgi {
        // Declares that this CorDapp requires the OSGi framework to provide the 'com.example.cordapp' package.
        // This value is passed straight through to Bnd.
        importPackage 'com.example.cordapp'

        // Declares that this CorDapp uses the 'com.example.cordapp.foo` package.
        // However, Corda will not complain if no-one provides it at runtime. This
        // assumes that the missing package isn't really required at all.
        optionalImport 'com.example.cordapp.foo'

        // Like `optionalImport`, except that it also assigns this package an empty
        // version range. This is useful when the unused package doesn't have a version
        // range of its own because it does not belong to another OSGi bundle.
        suppressImportVersion 'com.example.cordapp.bar'
    }
}
```

## ServiceLoader

Bundles that use `java.util.ServiceLoader` require special handling to support their `META-INF/services/` files.
Bnd provides [`@ServiceProvider` and `@ServiceConsumer` annotations](https://bnd.bndtools.org/chapters/240-spi-annotations.html)
to ensure that the bundle respects OSGi's [Service Loader Mediator Specification](https://docs.osgi.org/specification/osgi.cmpn/7.0.0/service.loader.html).

## Corda Metadata

The plugin will generate the following tags in the "main" jar's `MANIFEST.MF` by default:
- `Corda-Contract-Classes`
- `Corda-Flow-Classes`
- `Corda-MappedSchema-Classes`
- `Corda-Service-Classes`

Each tag contains a list of the classes within the jar that have been identified as being
a Corda contract, a Corda flow etc. Each of these classes has also been confirmed as being
public, static and non-abstract, which allows Corda to instantiate them. Empty tags are
excluded from the final manifest, and so not every tag is guaranteed to be present.

The plugin generates these lists using Bnd's `${classes}` macro. However, we may also need to
update these macros as Corda evolves, and would prefer not to need to update the `cordapp-cpk`
plugin at the same time. We can therefore update the macros by modifying the
```
net/corda/cordapp/cordapp-configuration.properties
```
file inside the `net.corda.cordapp.cordapp-configuration`
Gradle plugin, and then applying this new plugin to the CorDapps's root project. (The
`cordapp-configuration` plugin is part of the Corda repository, and new versions of it
will be released as part of Corda, of course.)
Any property key inside this file that matches `Corda-*-Classes` defines a filter to
generate a new manifest tag (or replace an existing tag). E.g.
```
Corda-Contract-Classes=IMPLEMENTS;net.corda.v10.ledger.contracts.Contract
```
The `cordapp-cpk` plugin will append additional clauses to each filter to ensure that it still
only selects public static non-abstract classes, since we don't expect this requirement to change.

### Dynamic Imports

The CPK needs to declare imports for these packages so that OSGi can create lazy proxies
for any JPA entities the bundle may contain:
- `org.hibernate.proxy`
- `javaassist.util.proxy`

We must also allow the bundle to import this package, which contains Hibernate-specific annotations:
- `org.hibernate.annotations`

We declare all these packages as "dynamic" imports to avoid binding the CPK to a specific
version of Hibernate, which should make it easier for Corda itself to evolve without breaking
everyone's CorDapps. (Future versions of Hibernate are also likely not to use Javassist.)

The plugin will declare these packages using the OSGI `DynamicImport-Package` header.

If necessary, we can update these package names via the `cordapp-configuration.properties`
file by adding a comma-separated list to the `Required-Packages` key:
```
Required-Packages=org.foo,org.bar
```

Note that doing this will completely override the plugin's hard-coded list of packages.

### Corda API Imports

Our goal is for any CPK written for Corda 5.x to be compatible with every release of Corda 5.x.
This requires the `cordapp-cpk` plugin to apply an explicit OSGi "consumer policy" for every
Corda API package that the CPK may use:
```
version='${range;[=,+);${@}}'
```
We identify Corda's API packages using the `Import-Policy-Packages` property:
```
Import-Policy-Packages=net.corda.v5.*
```
You can prevent the `cordapp-cpk` plugin from applying this policy by settings:
```gradle
jar {
    osgi {
        applyImportPolicy = false
    }
}
```
However, this will likely also prevent your CPK compiled for Corda 5.x from installing
correctly into any Corda node where
```
0 <= Corda version < x
```
