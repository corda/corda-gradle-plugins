package net.corda.flask.launcher

import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*


class JarCache2(appName : String) {

    companion object {
        private val log = LoggerFactory.getLogger(JarCache::class.java)

        private fun computeCacheDirectory(appName: String): Path {
            return when {
                OS.isUnix -> {
                    sequenceOf(
                        System.getenv("XDG_CACHE_HOME")?.let { prefix -> Paths.get(prefix, appName) },
                        System.getProperty("user.home")?.let { prefix -> Paths.get(prefix, ".cache", appName) },
                        System.getProperty("java.io.tmpdir")?.let { Paths.get(it).resolve(appName) },
                        Paths.get("/tmp", appName)
                    )
                }
                OS.isWindows -> {
                    sequenceOf(
                        System.getenv("LOCALAPPDATA")?.let { prefix -> Paths.get(prefix, ".cache", appName) },
                        System.getProperty("user.home")
                            ?.let { prefix -> Paths.get(prefix, "Local Settings", "Application Data", appName) },
                        System.getProperty("java.io.tmpdir")?.let { Paths.get(it).resolve(appName) }
                    )
                }
                OS.isMac -> {
                    sequenceOf(
                        System.getProperty("user.home")
                            ?.let { prefix -> Paths.get(prefix, "Library", "Saved Application State", appName) },
                        Paths.get("/Library/Caches", appName),
                        System.getProperty("java.io.tmpdir")?.let { Paths.get(it).resolve(appName) }
                    )
                }
                else -> {
                    sequenceOf(
                        System.getProperty("java.io.tmpdir")?.let { Paths.get(it).resolve(appName) }
                    )
                }
            }
            .filterNotNull()
            .filter(::validateCacheDirectory)
            .firstOrNull() ?: throw FileNotFoundException("Unable to find a usable cache directory")
        }

        private fun validateCacheDirectory(candidate: Path): Boolean {
            return try {
                if (!Files.exists(candidate)) {
                    Files.createDirectories(candidate)
                    true
                } else if (!Files.isDirectory(candidate)) {
                    log.debug("Cache directory '{}' discarded because it is not a directory", candidate.toString())
                    false
                } else if (!Files.isWritable(candidate)) {
                    log.debug("Cache directory '{}' discarded because it is not writable", candidate.toString())
                    false
                } else {
                    log.info("Using cache directory '{}'", candidate.toString())
                    true
                }
            } catch (ioe: Exception) {
                log.debug(
                    java.lang.String.format("Cache directory '%s' discarded: %s", candidate.toString(), ioe.message),
                    ioe
                )
                false
            }
        }
    }

    val path = computeCacheDirectory(appName)

    fun wipe() = Files.walk(path).sorted(Comparator.reverseOrder()).forEach(Files::delete)
}