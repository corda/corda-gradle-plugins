@file:JvmName("AsmTools")
package net.corda.gradle.jarfilter.asm

import net.corda.gradle.jarfilter.FILTER_FLAGS
import net.corda.gradle.jarfilter.descriptor
import net.corda.gradle.jarfilter.toPathFormat
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.security.ProtectionDomain


fun ByteArray.accept(visitor: (ClassVisitor) -> ClassVisitor): ByteArray {
    return ClassWriter(COMPUTE_MAXS).let { writer ->
        ClassReader(this).accept(visitor(writer), FILTER_FLAGS)
        writer.toByteArray()
    }
}

private val String.resourceName: String get() = "$toPathFormat.class"
val Class<*>.resourceName get() = name.resourceName
val Class<*>.bytecode: ByteArray get() = classLoader.getResourceAsStream(resourceName).use(InputStream::readBytes)
val Class<*>.descriptor: String get() = name.descriptor

/**
 * Functions for converting bytecode into a "live" Java class.
 */
inline fun <reified T: R, reified R: Any> ByteArray.toClass(): Class<out R> = toClass(T::class.java, R::class.java)

fun <T: R, R: Any> ByteArray.toClass(type: Class<in T>, asType: Class<out R>): Class<out R>
    = BytecodeClassLoader(this, type.name, type.protectionDomain, type.classLoader).createClass().asSubclass(asType)

private class BytecodeClassLoader(
    private val bytecode: ByteArray,
    private val className: String,
    private val protectionDomain: ProtectionDomain,
    parent: ClassLoader
) : ClassLoader(parent) {
    fun createClass(): Class<*> {
        return defineClass(className, bytecode, 0, bytecode.size, protectionDomain).apply(::resolveClass)
    }

    override fun findResource(name: String): URL? {
        return if (name == className.resourceName) {
            try {
                URL("file", "bytecode", className.resourceName)
            } catch (_: MalformedURLException) {
                null
            }
        } else {
            null
        }
    }

    // Ensure that the class we create also honours Class<*>.bytecode (above).
    override fun getResourceAsStream(name: String): InputStream? {
        return getResource(name)?.let { resource ->
            if (resource.protocol == "file"
                && resource.host == "bytecode"
                && resource.file == className.resourceName) {
                ByteArrayInputStream(bytecode)
            } else {
                try {
                    resource.openStream()
                } catch (_: IOException) {
                    null
                }
            }
        }
    }
}
