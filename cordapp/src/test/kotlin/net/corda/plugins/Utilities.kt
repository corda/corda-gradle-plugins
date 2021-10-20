package net.corda.plugins

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.fail

import java.nio.file.StandardCopyOption.REPLACE_EXISTING

private val classLoader: ClassLoader = object {}.javaClass.classLoader

@Throws(IOException::class)
fun installResource(folder: Path, resourceName: String): Long {
    val buildFile = folder.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')))
    return copyResourceTo(resourceName, buildFile)
}

@Throws(IOException::class)
fun copyResourceTo(resourceName: String, target: Path): Long {
    classLoader.getResourceAsStream(resourceName)?.use { input ->
        return Files.copy(input, target, REPLACE_EXISTING)
    } ?: fail("Resource '$resourceName' not found")
}

fun systemProperty(name: String): String = System.getProperty(name) ?: fail("System property '$name' not set.")
