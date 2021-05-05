package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.bytecode
import net.corda.gradle.jarfilter.asm.resourceName
import org.assertj.core.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.CRC32
import java.util.zip.Deflater.NO_COMPRESSION
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.*

/**
 * Creates a dummy jar containing the following:
 * - META-INF/MANIFEST.MF
 * - A compressed class file
 * - A compressed binary non-class file
 * - An uncompressed text file
 * - A directory entry
 *
 * The compression level is set to [NO_COMPRESSION]
 * in order to force the Gradle task to compress
 * the entries properly.
 */
class DummyJar(
    private val projectDir: Path,
    private val testClass: Class<*>,
    private val name: String
) {
    private companion object {
        private const val DATA_SIZE = 512

        @Suppress("SameParameterValue")
        private fun uncompressed(name: String, data: ByteArray) = ZipEntry(name).apply {
            size = data.size.toLong()
            compressedSize = size
            method = STORED
            crc = CRC32().let { crc ->
                crc.update(data)
                crc.value
            }
        }

        private fun compressed(name: String) = ZipEntry(name).apply { method = DEFLATED }

        private fun directoryOf(type: Class<*>)
            = directory(type.`package`.name.toPathFormat + '/')

        private fun directory(name: String) = ZipEntry(name).apply {
            method = STORED
            compressedSize = 0
            size = 0
            crc = 0
        }
    }

    private lateinit var _path: Path
    val path: Path get() = _path

    fun build(): DummyJar {
        val manifest = Manifest().apply {
            mainAttributes.also { main ->
                main[MANIFEST_VERSION] = "1.0"
            }
        }
        _path = projectDir.pathOf("${name}.jar")
        JarOutputStream(Files.newOutputStream(_path).buffered(), manifest).use { jar ->
            jar.setComment(testClass.name)
            jar.setLevel(NO_COMPRESSION)

            // One directory entry (stored)
            jar.putNextEntry(directoryOf(testClass))

            // One compressed class file
            jar.putNextEntry(compressed(testClass.resourceName))
            jar.write(testClass.bytecode)

            // One compressed non-class file
            jar.putNextEntry(compressed("binary.dat"))
            jar.write(arrayOfJunk(DATA_SIZE))

            // One uncompressed text file
            val text = """\
                |Jar: ${_path.toAbsolutePath()}
                |Class: ${testClass.name}
                |""".trimMargin().toByteArray()
            jar.putNextEntry(uncompressed("comment.txt", text))
            jar.write(text)
        }
        assertThat(_path).isRegularFile
        return this
    }
}
