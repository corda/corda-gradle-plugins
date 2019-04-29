package net.corda.gradle.jarfilter

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.util.zip.Deflater.BEST_COMPRESSION
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@Suppress("Unused")
open class MetaFixerTask @Inject constructor(objects: ObjectFactory, layouts: ProjectLayout) : DefaultTask() {
    private val _jars: ConfigurableFileCollection = project.files()
    @get:SkipWhenEmpty
    @get:InputFiles
    val jars: FileCollection
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

    @get:OutputFiles
    val metafixed: FileCollection get() = project.files(jars.map(::toMetaFixed))

    private fun toMetaFixed(source: File) = outputDir.file(suffix.map { sfx -> source.name.replace(JAR_PATTERN, "$sfx\$1") })

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
        private val target: Path = toMetaFixed(inFile).get().asFile.toPath()
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
                val entryData = inJar.getInputStream(entry)

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

    private fun Sequence<ZipEntry>.namesEndingWith(suffix: String): Set<String> {
        return filter { it.name.endsWith(suffix) }.map { it.name.dropLast(suffix.length) }.toSet()
    }
}

fun ByteArray.fixMetadata(logger: Logger, classNames: Set<String>): ByteArray
                  = execute({ writer -> MetaFixerVisitor(writer, logger, classNames) })
