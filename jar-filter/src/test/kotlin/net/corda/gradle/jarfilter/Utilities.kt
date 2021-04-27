@file:JvmName("Utilities")
package net.corda.gradle.jarfilter

import org.gradle.api.JavaVersion.current
import org.gradle.api.JavaVersion.VERSION_15
import org.gradle.util.GradleVersion
import org.opentest4j.TestAbortedException
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.reflect.Member
import java.net.MalformedURLException
import java.net.URLClassLoader
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors.toList
import java.util.zip.ZipFile
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.valueParameters

const val DEFAULT_MESSAGE = "<default-value>"
const val MESSAGE = "Goodbye, Cruel World!"
const val NUMBER = 111
const val BIG_NUMBER = 9999L

private val GRADLE_7 = GradleVersion.version("7.0")

private val classLoader: ClassLoader = MethodHandles.lookup().lookupClass().classLoader

// The TestAbortedException must be caught by the JUnit platform,
// which means that it must not be thrown when this class loads.
private val testGradleUserHomeValue: String? = System.getProperty("test.gradle.user.home")
private val testGradleUserHome: String get() = testGradleUserHomeValue
    ?: throw TestAbortedException("System property 'test.gradle.user.home' not set.")

fun getGradleArgsForTasks(vararg taskNames: String): MutableList<String> = getBasicArgsForTasks(*taskNames).apply { add("--info") }
fun getBasicArgsForTasks(vararg taskNames: String): MutableList<String> = mutableListOf(*taskNames, "--stacktrace", "-g", testGradleUserHome)

/**
 * We must execute [GradleRunner][org.gradle.testkit.runner.GradleRunner]
 * embedded in the existing Gradle instance in order to debug it. This
 * requires our current JVM toolchain to be compatible with Gradle.
 *
 * Gradle 6.x is compatible with Java 8 <= x <= Java 15.
 * Gradle 7.0 is compatible with Java 8 <= x <= Java 16.
 */
@Suppress("UnstableApiUsage")
fun isDebuggable(gradleVersion: GradleVersion): Boolean {
    return VERSION_15.isCompatibleWith(current()) || gradleVersion >= GRADLE_7
}

@Throws(IOException::class)
fun copyResourceTo(resourceName: String, target: Path) {
    classLoader.getResourceAsStream(resourceName)?.use { source ->
        Files.copy(source, target, REPLACE_EXISTING)
    }
}

@Throws(IOException::class)
fun Path.installResources(vararg resourceNames: String) {
    resourceNames.forEach { installResource(it) }
}

@Throws(IOException::class)
fun Path.installResource(resourceName: String): Path = resolve(resourceName.fileName).let { path ->
    copyResourceTo(resourceName, path)
    path
}

private val String.fileName: String get() = substring(1 + lastIndexOf('/'))

val String.toPackageFormat: String get() = replace('/', '.')
fun pathsOf(vararg types: KClass<*>): Set<String> = types.mapTo(LinkedHashSet()) { it.java.name.toPathFormat }

fun Path.pathOf(vararg elements: String): Path = Paths.get(toAbsolutePath().toString(), *elements)

fun arrayOfJunk(size: Int) = ByteArray(size).apply {
    for (i in indices) {
        this[i] = i.toByte()
    }
}

fun Member.hasModifiers(flags: Int): Boolean {
    return (modifiers and flags) == flags
}

val KFunction<*>.hasAnyOptionalParameters: Boolean
    get() = valueParameters.any(KParameter::isOptional)

val KFunction<*>.hasAllOptionalParameters: Boolean
    get() = valueParameters.all(KParameter::isOptional)

val KFunction<*>.hasAllMandatoryParameters: Boolean
    get() = valueParameters.none(KParameter::isOptional)

val <T : Any> KClass<T>.noArgConstructor: KFunction<T>?
    get() = constructors.firstOrNull(KFunction<*>::hasAllOptionalParameters)

@Throws(MalformedURLException::class)
fun classLoaderFor(jar: Path) = URLClassLoader(arrayOf(jar.toUri().toURL()), classLoader)

@Suppress("UNCHECKED_CAST")
@Throws(ClassNotFoundException::class)
fun <T> ClassLoader.load(className: String)
            = Class.forName(className, true, this) as Class<T>

fun Path.getClassNames(prefix: String): List<String> {
    val resourcePrefix = prefix.toPathFormat
    return ZipFile(toFile()).use { zip ->
        zip.stream().filter { it.name.startsWith(resourcePrefix) && it.name.endsWith(".class") }
           .map { it.name.removeSuffix(".class").toPackageFormat }
           .collect(toList<String>())
    }
}
