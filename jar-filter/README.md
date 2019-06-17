# JarFilter

Deletes annotated elements at the byte-code level from a JAR of Java/Kotlin code. In the case of Kotlin
code, it also modifies the `@kotlin.Metadata` annotations not to contain any functions, properties or
type aliases that have been deleted. This prevents the Kotlin compiler from successfully compiling against
any elements which no longer exist.

We use this plugin together with ProGuard to generate Corda's `core-deterministic` and `serialization-deterministic`
modules. See [here](https://github.com/corda/corda/blob/master/docs/source/deterministic-modules.rst) for more information.

## Usage
The plugin only needs to be added to the Gradle plugin classpath to make its task classes available. You can then use
these classes to declare tasks in your `build.gradle` files.
```gradle
buildscript {
    repositories {
        gradlePluginPortal()
    }

    classpath "net.corda.plugins:jar-filter:$jar-filter-version"
}
```
or
```gradle
plugins {
    id 'net.corda.plugins.jar-filter' version '$jar-filter-version' apply false
}
```

You can enable the tasks' logging output using Gradle's `--info` or `--debug` command-line options.

### The `JarFilter` task
The `JarFilter` task removes unwanted elements from `class` files, namely:
- Deleting both Java methods/fields and Kotlin functions/properties/type aliases.
- Stubbing out methods by replacing the byte-code of their implementations.
- Removing annotations from classes/methods/fields.

It supports the following configuration options:
```gradle
import net.corda.gradle.jarfilter.JarFilterTask
task jarFilter(type: JarFilterTask) {
    // Task(s) whose JAR outputs should be filtered.
    jars jar

    // The annotations assigned to each filtering role. For example:
    annotations {
        forDelete = [
            "org.testing.DeleteMe"
        ]
        forStub = [
            "org.testing.StubMeOut"
        ]
        forRemove = [
            "org.testing.RemoveMe"
        ]
    }

    // Location for filtered JARs. Defaults to "$buildDir/filtered-libs".
    outputDir file(...)

    // Whether the timestamps on the JARs' entries should be preserved "as is"
    // or set to a platform-independent constant value (1st February 1980).
    preserveTimestamps = {true|false}

    // The maximum number of times (>= 1) to pass the JAR through the filter.
    maxPasses = 5

    // Writes more information about each pass of the filter.
    verbose = {true|false}
}
```

You can specify as many annotations for each role as you like. The only constraint is that a given
annotation cannot be assigned to more than one role.

#### Removing unwanted default parameter values
It is possible to assign non-deterministic expressions as default values for Kotlin constructors and functions. For
example:
```kotlin
data class UniqueIdentifier(val externalId: String? = null, val id: UUID = UUID.randomUUID())
```

The Kotlin compiler will generate _two_ constructors in this case:
```
UniqueIdentifier(String?, UUID)
UniqueIdentifier(String?, UUID, Int, DefaultConstructorMarker)
```

The first constructor is the primary constructor that we would expect (and which we'd like to keep), whereas the
second is a public synthetic constructor that Kotlin applications invoke to handle the different combinations of
default parameter values. Unfortunately, this synthetic constructor is therefore also part of the Kotlin ABI and
so we _cannot_ rewrite the class like this to remove the default values:
```kotlin
// THIS REFACTOR WOULD BREAK THE KOTLIN ABI!
data class UniqueIdentifier(val externalId: String?, val id: UUID) {
    constructor(externalId: String?) : this(externalId, UUID.randomUUID())
    constructor() : this(null)
}
```

The refactored class would have the following constructors, and would require client applications to be recompiled:
```
UniqueIdentifier(String?, UUID)
UniqueIdentifier(String?)
UniqueIdentifier()
```

We therefore need to keep the default constructor parameters in order to preserve the ABI for the unfiltered code,
which in turn means that `JarFilter` will need to delete only the synthetic constructor and leave the primary
constructor intact. However, Kotlin does not currently allow us to annotate _specific_ constructors - see
[KT-22524](https://youtrack.jetbrains.com/issue/KT-22524). Until it does, `JarFilter` will perform an initial
"sanitising" pass over the JAR file to remove any unwanted annotations from the primary constructors. These unwanted
annotations are configured in the `JarFilter` task definition:
```gradle
task jarFilter(type: JarFilterTask) {
    ...
    annotations {
        ...
        forSanitise = [
            "org.testing.DeleteMe"
        ]
    }
}
```

This allows us to annotate the `UniqueIdentifier` class like this:
```kotlin
data class UniqueIdentifier @DeleteMe constructor(val externalId: String? = null, val id: UUID = UUID.randomUUID())
```

to generate these constructors:
```
UniqueIdentifier(String?, UUID)
@DeleteMe UniqueIdentifier(String?, UUID, Int, DefaultConstructorMarker)
```

We currently **do not** sanitise annotations from functions with default parameter values, although (in theory) these
may also be non-deterministic. We will need to extend the sanitation pass to include such functions if/when the need
arises. At the moment, deleting such functions _entirely_ is enough, whereas also deleting a primary constructor means
that we can no longer create instances of that class either.

### The `MetaFixer` task
The `MetaFixer` task updates the `@kotlin.Metadata` annotations by removing references to any functions,
constructors, properties or nested classes that no longer exist in the byte-code. This is primarily to
"repair" Kotlin library code that has been processed by ProGuard.

Kotlin type aliases exist only inside `@Metadata` and so are unaffected by this task. Similarly, the
constructors for Kotlin's annotation classes don't exist in the byte-code either because Java annotations
are interfaces really. The `MetaFixer` task will therefore ignore annotations' constructors too.

It supports these configuration options:
```gradle
import net.corda.gradle.jarfilter.MetaFixerTask
task metafix(type: MetaFixerTask) {
    // Task(s) whose JAR outputs should be fixed.
    jars jar

    // Location for fixed JARs. Defaults to "$buildDir/metafixed-libs"
    outputDir file(...)

    // Tag to be appended to the JAR name. Defaults to "-metafixed".
    suffix = "..."

    // Whether the timestamps on the JARs' entries should be preserved "as is"
    // or set to a platform-independent constant value (1st February 1980).
    preserveTimestamps = {true|false}
}
```

## Implementation Details

### Code Coverage
You can generate a JaCoCo code coverage report for the unit tests using:
```bash
$ cd buildSrc
$ ../gradlew jar-filter:jacocoTestReport
```

### Kotlin Metadata
The Kotlin compiler encodes information about each class inside its `@kotlin.Metadata` annotation.

```kotlin
package kotlin

import kotlin.annotation.AnnotationRetention.*

@Retention(RUNTIME)
annotation class Metadata {
    val k: Int = 1
    val d1: Array<String> = []
    val d2: Array<String> = []
    // ...
}
```

This is an internal feature of Kotlin which is read by Kotlin Reflection. The public API for
manipulating this information is `kotlin-metadata-jvm`, and requires that we first extract the `d1`
and `d2` fields from the `@kotlin.Metadata` annotation using (say) the ASM library. The data format
for these arrays depends upon the "class kind" `k`. For the kinds that we are interested in, `d1`
contains a buffer of ProtoBuf data and `d2` contains an array of `String` identifiers which the
ProtoBuf data refers to by index. The `kotlin-metadata-jvm` library translates this ProtoBuf data
into recognisable value objects so that we can remove those elements corresponding to the deleted
byte-code. The library then converts the remaining elements back into ProtoBuf arrays that ASM can
write into `kotlin.Metadata` annotation.

### JARs vs ZIPs
The `JarFilter` and `MetaFixer` tasks _deliberately_ use `ZipFile` and `ZipOutputStream` rather
than `JarInputStream` and `JarOutputStream` when reading and writing their JAR files. This is to
ensure that the original `META-INF/MANIFEST.MF` files are passed through unaltered. Note also that
there is no `ZipInputStream.getComment()` method, and so we need to use `ZipFile` in order to
preserve any JAR comments.

Neither `JarFilter` nor `MetaFixer` should change the order of the entries inside the JAR files.
