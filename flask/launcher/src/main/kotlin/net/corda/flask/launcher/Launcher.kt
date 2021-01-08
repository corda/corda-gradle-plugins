package net.corda.flask.launcher

import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.jar.Attributes
import java.util.jar.Manifest

object Launcher2 {

    object ManifestAttributes {
        val MD5_DIGEST = "MD5-Digest"
        val APPLICATION_NAME = "Application-Name"
        val APPLICATION_CLASS = "Application-Class"
        val JVM_ARGS = "JVM-Args"
    }

    @JvmStatic
    fun main(argv: Array<String>) {
//        System.setProperty("java.util.logging.config.file", "logging.properties")
//        val manifest = javaClass.getResourceAsStream("/META-INF/MANIFEST.MF").use(::Manifest)
//        val appName = manifest.mainAttributes.getValue(ManifestAttributes.APPLICATION_NAME)
//        val cache = JarCache(appName)
//        LockFile.acquire(cache.path.resolve("flask.lock"), true).use {
//            val libraries : MutableList<Path> = mutableListOf()
//            val buffer = ByteArray(0x10000)
//            manifest.entries.filter {
//                it.key.startsWith("/LIB-INF/") && it.value.getValue(ManifestAttributes.MD5_DIGEST) != null
//            }.forEach { (jarEntryName, attributes) ->
//                val jarName = jarEntryName.substring(jarEntryName.lastIndexOf('/') + 1)
//                val hash = attributes.getValue(ManifestAttributes.MD5_DIGEST)
//                val destination = cache.path.resolve("lib").resolve(hash).resolve(jarName)
//                libraries.add(destination)
//                if(!Files.exists(destination)) {
//                    Files.createDirectories(destination.parent)
//                    javaClass.getResourceAsStream(jarEntryName)?.use { inputStream ->
//                        Files.newOutputStream(destination).buffered().use { os ->
//                            while(true) {
//                                val read = inputStream.read(buffer)
//                                if(read < 0) break
//                                os.write(buffer, 0, read)
//                            }
//                        }
//                    } ?: throw RuntimeException("Entry '$jarEntryName' missing from flask jar")
//                }
//            }
//            JavaProcessBuilder(manifest.mainAttributes.getValue(ManifestAttributes.APPLICATION_CLASS)).apply {
//                manifest.mainAttributes.getValue(ManifestAttributes.JVM_ARGS)?.let {
//                    jvmArgs += it.split(" ")
//                }
//                classpath += libraries.map(Path::toString)
//                cliArgs += argv
//                manifest.mainAttributes["JVM-Properties"]?.let { jvmProperties ->
//                    (jvmProperties as String).split(' ').forEach {
//                        properties.setProperty()
//                    }
//                }
//                properties.apply {
//                    manifest.getAttributes("JVM-Properties").entries.stream().forEach {
//                        setProperty(it.key.toString(), it.value.toString())
//                    }
//                }
//            }.exec()
//            val now = FileTime.from(Instant.now())
//            libraries.forEach {
//                Files.setLastModifiedTime(it, now)
//            }
//        }
    }
}