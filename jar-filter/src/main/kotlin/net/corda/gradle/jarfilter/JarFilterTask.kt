package net.corda.gradle.jarfilter

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.StandardCopyOption.*
import java.util.zip.Deflater.BEST_COMPRESSION
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.math.max

@Suppress("Unused")
open class JarFilterTask @Inject constructor(objects: ObjectFactory, layouts: ProjectLayout) : DefaultTask() {
    private companion object {
        private const val DEFAULT_MAX_PASSES = 5
    }

    private val _jars: ConfigurableFileCollection = project.files()
    @get:SkipWhenEmpty
    @get:InputFiles
    val jars: FileCollection get() = _jars

    @Suppress("MemberVisibilityCanBePrivate")
    fun setJars(inputs: Any?) {
        val files = inputs ?: return
        _jars.setFrom(files)
    }

    fun jars(inputs: Any?) = setJars(inputs)

    @get:Nested
    val annotations: FilterAnnotations = objects.newInstance(FilterAnnotations::class.java)

    fun annotations(action: Action<in FilterAnnotations>) {
        action.execute(annotations)
    }

    @get:Console
    val verbose: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(false)

    @get:Input
    val maxPasses: Property<Int> = objects.property(Int::class.javaObjectType).convention(DEFAULT_MAX_PASSES)

    @get:Input
    val preserveTimestamps: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)

    @get:Internal
    val outputDir: DirectoryProperty = objects.directoryProperty().convention(layouts.buildDirectory.dir("filtered-libs"))

    fun outputDir(dir: File) {
        outputDir.set(dir)
    }

    @get:OutputFiles
    val filtered: FileCollection get() = project.files(jars.map(::toFiltered))

    private fun toFiltered(source: File) = outputDir.file(source.name.replace(JAR_PATTERN, "-filtered\$1"))

    @TaskAction
    fun filterJars() {
        logger.info("JarFiltering:")
        val annotationValues = annotations.values
        with(annotationValues.forDelete) {
            if (isNotEmpty()) {
                logger.info("- Elements annotated with one of '{}' will be deleted", joinToString())
            }
        }
        with(annotationValues.forStub) {
            if (isNotEmpty()) {
                logger.info("- Methods annotated with one of '{}' will be stubbed out", joinToString())
            }
        }
        with(annotationValues.forRemove) {
            if (isNotEmpty()) {
                logger.info("- Annotations '{}' will be removed entirely", joinToString())
            }
        }
        with(annotationValues.forSanitise) {
            if (isNotEmpty()) {
                logger.info("- Annotations '{}' will be removed from primary constructors", joinToString())
            }
        }
        checkDistinctAnnotations(annotationValues)
        try {
            for (jar in jars) {
                logger.info("Filtering {}", jar)
                Filter(jar, annotationValues).run()
            }
        } catch (e: Exception) {
            throw e.asUncheckedException()
        }
    }

    private fun checkDistinctAnnotations(annotationValues: FilterAnnotations.Values) = with(annotationValues) {
        logger.info("Checking that all annotations are distinct.")
        val allAnnotations = (forRemove + forDelete + forStub - forRemove).toMutableSet()
        forDelete.forEach {
            if (!allAnnotations.remove(it)) {
                failWith("Annotation '$it' also appears in JarFilter 'forDelete' section")
            }
        }
        forStub.forEach {
            if (!allAnnotations.remove(it)) {
                failWith("Annotation '$it' also appears in JarFilter 'forStub' section")
            }
        }
        if (allAnnotations.isNotEmpty()) {
            failWith("SHOULDN'T HAPPEN - Martian annotations! '${allAnnotations.joinToString()}'")
        }
    }

    private fun failWith(message: String): Nothing = throw InvalidUserDataException(message)

    private fun verbose(format: String, vararg objects: Any) {
        if (verbose.get()) {
            logger.info(format, *objects)
        }
    }

    private inner class Filter(inFile: File, private val annotationValues: FilterAnnotations.Values) {
        private val unwantedElements = UnwantedCache()
        private val source: Path = inFile.toPath()
        private val target: Path = toFiltered(inFile).get().asFile.toPath()

        private val descriptorsForRemove = toDescriptors(annotationValues.forRemove)
        private val descriptorsForDelete = toDescriptors(annotationValues.forDelete)
        private val descriptorsForStub = toDescriptors(annotationValues.forStub)
        private val descriptorsForSanitising = toDescriptors(annotationValues.forSanitise)

        init {
            Files.deleteIfExists(target)
        }

        fun run() {
            logger.info("Filtering to: {}", target)
            var input = source

            try {
                if (descriptorsForSanitising.isNotEmpty() && SanitisingPass(input).use(Pass::run)) {
                    input = target.moveToInput()
                }

                val maxPasses = max(this@JarFilterTask.maxPasses.get(), 1)
                var passes = 1
                while (true) {
                    verbose("Pass {}", passes)
                    val isModified = FilterPass(input).use(FilterPass::run)

                    if (!isModified) {
                        logger.info("No changes after latest pass - exiting.")
                        break
                    } else if (++passes > maxPasses) {
                        logger.warn("Exceeded maximum number of passes ({}) - aborting!", maxPasses)
                        break
                    }

                    input = target.moveToInput()
                }
            } catch (e: Exception) {
                val filterAnnotations = arrayListOf(annotationValues.forRemove) + annotationValues.forDelete + annotationValues.forStub
                logger.error("Error filtering '{}' elements from {}", filterAnnotations, input)
                throw e
            }
        }

        private fun Path.moveToInput(): Path {
            return Files.move(this, Files.createTempFile(parent, "filter-", ".tmp"), REPLACE_EXISTING).also {
                verbose("New input JAR: {}", it)
            }
        }

        private abstract inner class Pass(input: Path): Closeable {
            /**
             * Use [ZipFile] instead of [java.util.jar.JarInputStream] because
             * JarInputStream consumes MANIFEST.MF when it's the first or second entry.
             */
            protected val inJar = ZipFile(input.toFile())
            protected val outJar = ZipOutputStream(Files.newOutputStream(target))
            protected var isModified = false

            @Throws(IOException::class)
            override fun close() {
                inJar.use {
                    outJar.close()
                }
            }

            abstract fun transform(inBytes: ByteArray): ByteArray

            fun run(): Boolean {
                outJar.setLevel(BEST_COMPRESSION)
                outJar.setComment(inJar.comment)

                for (entry in inJar.entries()) {
                    val entryData = inJar.getInputStream(entry)

                    if (entry.isDirectory || !entry.name.endsWith(".class")) {
                        // This entry's byte contents have not changed,
                        // but may still need to be recompressed.
                        outJar.putNextEntry(entry.copy().withFileTimestamps(preserveTimestamps.get()))
                        entryData.copyTo(outJar)
                    } else {
                        val classData = transform(entryData.readBytes())
                        if (classData.isNotEmpty()) {
                            // This entry's byte contents have almost certainly
                            // changed, and will be stored compressed.
                            outJar.putNextEntry(entry.asCompressed().withFileTimestamps(preserveTimestamps.get()))
                            outJar.write(classData)
                        }
                    }
                }
                return isModified
            }
        }

        private inner class SanitisingPass(input: Path) : Pass(input) {
            override fun transform(inBytes: ByteArray): ByteArray {
                return ClassWriter(0).let { writer ->
                    val transformer = SanitisingTransformer(writer, logger, descriptorsForSanitising)
                    ClassReader(inBytes).accept(transformer, 0)
                    isModified = isModified or transformer.isModified
                    writer.toByteArray()
                }
            }
        }

        private inner class FilterPass(input: Path) : Pass(input) {
            override fun transform(inBytes: ByteArray): ByteArray {
                var reader = ClassReader(inBytes)
                var writer = ClassWriter(COMPUTE_MAXS)
                var transformer = FilterTransformer(
                    visitor = writer,
                    logger = logger,
                    removeAnnotations = descriptorsForRemove,
                    deleteAnnotations = descriptorsForDelete,
                    stubAnnotations = descriptorsForStub,
                    unwantedElements = unwantedElements
                )

                /*
                 * First pass: This might not find anything to remove!
                 */
                reader.accept(transformer, 0)

                if (transformer.isUnwantedClass || transformer.hasUnwantedElements) {
                    isModified = true

                    do {
                        /*
                         * Rewrite the class without any of the unwanted elements.
                         * If we're deleting the class then make sure we identify all of
                         * its inner classes too, for the next filter pass to delete.
                         */
                        reader = ClassReader(writer.toByteArray())
                        writer = ClassWriter(COMPUTE_MAXS)
                        transformer = transformer.recreate(writer)
                        reader.accept(transformer, 0)
                    } while (!transformer.isUnwantedClass && transformer.hasUnwantedElements)
                }

                return if (transformer.isUnwantedClass) {
                    // The entire class is unwanted, so don't write it out.
                    logger.info("Deleting class {}", transformer.className)
                    byteArrayOf()
                } else {
                    writer.toByteArray()
                }
            }
        }
    }
}
