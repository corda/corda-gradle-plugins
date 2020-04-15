@file:JvmName("Utils")
package net.corda.gradle.jarfilter

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.logging.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.Calendar.FEBRUARY
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.DEFLATED
import java.util.zip.ZipEntry.STORED
import kotlin.math.max
import kotlin.text.RegexOption.*

const val GROUP_NAME = "JarFilter"
const val FILTER_FLAGS = SKIP_DEBUG and SKIP_FRAMES

@JvmField
val JAR_PATTERN = "(\\.jar)\$".toRegex(IGNORE_CASE)

// Use the same constant file timestamp as Gradle.
private val CONSTANT_TIME: FileTime = FileTime.fromMillis(
    GregorianCalendar(1980, FEBRUARY, 1).apply { timeZone = TimeZone.getTimeZone("UTC") }.timeInMillis
)

// Declared as inline to avoid polluting the exception stack trace.
@Suppress("NOTHING_TO_INLINE")
inline fun Exception.asUncheckedException(): RuntimeException
    = (this as? RuntimeException) ?: InvalidUserCodeException(message ?: "", this)

/**
 * Recreates a [ZipEntry] object. The entry's byte contents
 * will be compressed automatically, and its CRC, size and
 * compressed size fields populated.
 */
fun ZipEntry.asCompressed(): ZipEntry {
    return ZipEntry(name).also { entry ->
        entry.lastModifiedTime = lastModifiedTime
        lastAccessTime?.also { at -> entry.lastAccessTime = at }
        creationTime?.also { ct -> entry.creationTime = ct }
        entry.comment = comment
        entry.method = DEFLATED
        entry.extra = extra
    }
}

fun ZipEntry.copy(): ZipEntry {
    return if (method == STORED) ZipEntry(this) else asCompressed()
}

fun ZipEntry.withFileTimestamps(preserveTimestamps: Boolean): ZipEntry {
    if (!preserveTimestamps) {
        lastModifiedTime = CONSTANT_TIME
        lastAccessTime?.apply { lastAccessTime = CONSTANT_TIME }
        creationTime?.apply { creationTime = CONSTANT_TIME }
    }
    return this
}

/**
 * Converts Java class names to Java descriptors.
 */
fun toDescriptors(classNames: Iterable<String>): Set<String> {
    return classNames.mapTo(LinkedHashSet(), String::descriptor)
}

val String.toPathFormat: String get() = replace('.', '/')
val String.descriptor: String get() = "L$toPathFormat;"


/**
 * Performs the given number of passes of the repeatable visitor over the byte-code.
 * Used by [MetaFixerVisitor], but also by some of the test visitors.
 */
fun <T> ByteArray.execute(visitor: (ClassVisitor) -> T, flags: Int = 0, passes: Int = 2): ByteArray
    where T : ClassVisitor,
          T : Repeatable<T> {
    var bytecode = this
    var writer = ClassWriter(flags)
    var transformer = visitor(writer)
    var count = max(passes, 1)

    while (--count >= 0) {
        ClassReader(bytecode).accept(transformer, FILTER_FLAGS)
        bytecode = writer.toByteArray()

        if (!transformer.hasUnwantedElements) {
            break
        }

        writer = ClassWriter(flags)
        transformer = transformer.recreate(writer)
    }

    return bytecode
}

fun ByteArray.fixMetadata(logger: Logger, classNames: Set<String>): ByteArray
        = execute({ writer -> MetaFixerVisitor(writer, logger, classNames) })
