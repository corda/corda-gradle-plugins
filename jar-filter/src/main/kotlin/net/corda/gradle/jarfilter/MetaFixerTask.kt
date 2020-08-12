package net.corda.gradle.jarfilter

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater.BEST_COMPRESSION
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@Suppress("Unused", "UnstableApiUsage")
open class MetaFixerTask @Inject constructor(objects: ObjectFactory, layouts: ProjectLayout) : DefaultTask() {
    init {
        description = "Rewrites kotlin.Metadata annotations to match their classes' methods and fields."
        group = GROUP_NAME
    }

    private val _jars: ConfigurableFileCollection = objects.fileCollection()
    val jars: FileCollection
        @PathSensitive(RELATIVE)
        @SkipWhenEmpty
        @InputFiles
        get() = _jars

    @Suppress("MemberVisibilityCanBePrivate")
    fun setJars(inputs: Any?) {
        val files = inputs ?: return
        _jars.setFrom(files)
    }

    fun jars(inputs: Any?) = setJars(inputs)

    @get:Internal
    val outputDir: DirectoryProperty = objects.directoryProperty().convention(layouts.buildDirectory.dir("metafixer-libs"))

    fun outputDir(dir: File) {
        outputDir.set(dir)
    }

    @get:Input
    val suffix: Property<String> = objects.property(String::class.java).convention("-metafixed")

    fun suffix(sfx: String?) = suffix.set(sfx)

    @get:Input
    val preserveTimestamps: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)

    private val _metafixed: ConfigurableFileCollection = objects.fileCollection().from(outputDir.map { dir ->
        _jars.elements.map { files ->
            files.map { file -> toMetaFixed(dir, file) }
        }
    })
    val metafixed: FileCollection
        @OutputFiles
        get() {
            // Don't compute these values more than once.
            // Replace with finalizeValueOnRead() immediately after
            // construction when we upgrade this plugin to Gradle 6.1.
            _metafixed.finalizeValue()
            return _metafixed
        }

    private fun toMetaFixed(dir: Directory, source: File): Provider<RegularFile> {
        return dir.file(suffix.map { sfx -> source.name.replace(JAR_PATTERN, "$sfx\$1") })
    }

    private fun toMetaFixed(dir: Directory, source: FileSystemLocation): Provider<RegularFile> = toMetaFixed(dir, source.asFile)

    @TaskAction
    fun fixMetadata() {
        logger.info("Fixing Kotlin @Metadata")
        try {
            for (jar in jars) {
                logger.info("Reading from {}", jar)
                MetaFix(jar).use(MetaFix::run)
            }
        } catch (e: Exception) {
            throw e.asUncheckedException()
        }
    }

    private inner class MetaFix(inFile: File) : Closeable {
        /**
         * Use [ZipFile] instead of [java.util.jar.JarInputStream] because
         * JarInputStream consumes MANIFEST.MF when it's the first or second entry.
         */
        private val target: Path = outputDir.flatMap { dir -> toMetaFixed(dir, inFile) }.get().asFile.toPath()
        private val inJar = ZipFile(inFile)
        private val outJar: ZipOutputStream

        init {
            // Default options for newOutputStream() are CREATE, TRUNCATE_EXISTING.
            outJar = ZipOutputStream(Files.newOutputStream(target)).apply {
                setLevel(BEST_COMPRESSION)
            }
        }

        @Throws(IOException::class)
        override fun close() {
            inJar.use {
                outJar.close()
            }
        }

        fun run() {
            logger.info("Writing to {}", target)
            outJar.setComment(inJar.comment)

            val classNames = inJar.entries().asSequence().namesEndingWith(".class")
            for (entry in inJar.entries()) {
                inJar.getInputStream(entry).use { entryData ->
                    if (entry.isDirectory || !entry.name.endsWith(".class")) {
                        // This entry's byte contents have not changed,
                        // but may still need to be recompressed.
                        outJar.putNextEntry(entry.copy().withFileTimestamps(preserveTimestamps.get()))
                        entryData.copyTo(outJar)
                    } else {
                        // This entry's byte contents have almost certainly
                        // changed, and will be stored compressed.
                        val classData = entryData.readBytes().fixMetadata(logger, classNames)
                        outJar.putNextEntry(entry.asCompressed().withFileTimestamps(preserveTimestamps.get()))
                        outJar.write(classData)
                    }
                }
            }
        }
    }

    private fun Sequence<ZipEntry>.namesEndingWith(suffix: String): Set<String> {
        return filter { it.name.endsWith(suffix) }.mapTo(LinkedHashSet()) { it.name.dropLast(suffix.length) }
    }
}
