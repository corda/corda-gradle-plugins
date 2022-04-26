# Cordapp Gradle Plugin

## Purpose

To transform any project this plugin is applied to into a cordapp project that generates a cordapp JAR.

## Effects

Will modify the default JAR task to create a CorDapp format JAR instead [see here](https://docs.corda.net/cordapp-build-systems.html) 
for more information.

## Migrating from Corda Gradle plugins 5.0.x

The original `cordaCompile` and `cordaRuntime` configurations were built using Gradle's deprecated `compile` and `runtime`
configurations, which means that they are no longer compatible with Gradle 7+.

- `cordaCompile` dependencies have been replaced by `cordaProvided`. These are `implementation` dependencies that the
  CorDapp needs at compile time, but which should not be bundled into the CorDapp jar because they will be provided by
  Corda itself at runtime.
- `cordaRuntime` dependencies have been replaced by `cordaRuntimeOnly`. These are `runtimeOnly` dependencies which
  should also not be bundled into the CorDapp jar. You are unlikely to need this configuration in practice when building
  a CorDapp.

The `cordapp` plugin applies Gradle's `java-library` plugin automatically.

### Signing Options

The DSL for the CorDapp signing options has been updated as follows:

```
cordapp {
    signing {
        enabled = <Boolean> (true)
        options {
            keyStore = <URI> or <File>
            storeType = <String> (JKS)
            storePassword = <String>
            keyPassword = <String>
            alias = <String>
            signatureFileName = <String>
            signatureAlgorithm = <String>
            digestAlgorithm = <String>
            tsaUrl = <optional URI>
            tsaCert = <optional String>
            tsaDigestAlgorithm = <optional String>
            tsaProxyHost = <optional String>
            tsaProxyPort = <optional Int>
            preserveLastModified = <Boolean> (false)
            verbose = <Boolean> (false)
            strict = <Boolean> (false0
            internalSF = <Boolean> (false)
            sectionsOnly = <Boolean> (false)
            lazy = <Boolean> (false)
            force = <Boolean> (false)
            maxMemory = <String>
            executable = <optional File>
        }
    }
}
```

The plugin uses the built-in "development" keystore by default. However, you can also configure it to use a different
keystore by setting these Java system properties when building the CorDapp:

- `signing.keystore`
- `signing.storetype`
- `signing.alias`
- `signing.storepass`
- `signing.keypass`
- `signing.sigfile`

**_You are strongly advised NOT to release your CorDapp still signed with this development key!_**

### Publishing

The CorDapp artifact is added to a new `cordapp` component, so publication now looks something like this:
```
plugins {
    id 'net.corda.plugins.cordapp'
    id 'maven-publish'
}

publishing {
    publications {
        myCordapp(MavenPublication) {
            from components.cordapp
        }
    }
}
```
Any other components (e.g. `java`, `kotlin`) are deleted to prevent them from being used accidentally.
