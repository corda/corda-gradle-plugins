@file:JvmName("MetadataTools")
package net.corda.gradle.jarfilter.asm

import kotlinx.metadata.InconsistentKotlinMetadataException
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import net.corda.gradle.jarfilter.*
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ASM7

@Suppress("UNCHECKED_CAST")
private val metadataClass: Class<out Annotation>
                = object {}.javaClass.classLoader.loadClass("kotlin.Metadata") as Class<out Annotation>

/**
 * Rewrite the bytecode for this class with the Kotlin @Metadata of another class.
 */
inline fun <reified T: Any, reified X: Any> recodeMetadataFor(): ByteArray = T::class.java.metadataAs(X::class.java)

fun <T: Any, X: Any> Class<in T>.metadataAs(template: Class<in X>): ByteArray {
    val metadata = template.readMetadata().let { m ->
        val templateDescriptor = template.descriptor
        val templatePrefix = templateDescriptor.dropLast(1) + '$'
        val targetDescriptor = descriptor
        val targetPrefix = targetDescriptor.dropLast(1) + '$'
        KotlinClassHeader(
            m.kind,
            m.metadataVersion,
            m.bytecodeVersion,
            m.data1,
            m.data2.map { s ->
                when {
                    // Replace any references to the template class with the target class.
                    s == templateDescriptor -> targetDescriptor
                    s.startsWith(templatePrefix) -> targetPrefix + s.substring(templatePrefix.length)
                    else -> s
                }
            }.toTypedArray(),
            m.extraString,
            m.packageName,
            m.extraInt
        )
    }
    return bytecode.accept { w -> MetadataWriter(metadata, w) }
}

/**
 * Kotlin reflection only supports classes atm, so use this to examine file metadata.
 */
internal val Class<*>.fileMetadata: FileMetadata get() {
    val fileMetadata = FileMetadata()
    (KotlinClassMetadata.read(readMetadata()) as? KotlinClassMetadata.FileFacade
        ?: throw InconsistentKotlinMetadataException("Unknown metadata format")).accept(fileMetadata)
    return fileMetadata
}

/**
 * For accessing the parts of class metadata that Kotlin reflection cannot reach.
 */
internal val Class<*>.classMetadata: ClassMetadata get() {
    val classMetadata = ClassMetadata()
    (KotlinClassMetadata.read(readMetadata()) as? KotlinClassMetadata.Class
        ?: throw InconsistentKotlinMetadataException("Unknown metadata format")).accept(classMetadata)
    return classMetadata
}

private fun Class<*>.readMetadata(): KotlinClassHeader {
    val metadata = getAnnotation(metadataClass)
    val kind = metadataClass.getMethod(KOTLIN_KIND_FIELD_NAME)
    val metadataVersion = metadataClass.getMethod(KOTLIN_METADATA_VERSION_NAME)
    val bytecodeVersion = metadataClass.getMethod(KOTLIN_BYTECODE_VERSION_NAME)
    val data1 = metadataClass.getMethod(KOTLIN_METADATA_DATA_FIELD_NAME)
    val data2 = metadataClass.getMethod(KOTLIN_METADATA_STRINGS_FIELD_NAME)
    val extraString = metadataClass.getMethod(KOTLIN_METADATA_EXTRA_STRING_NAME)
    val packageName = metadataClass.getMethod(KOTLIN_METADATA_PACKAGE_NAME)
    val extraInt = metadataClass.getMethod(KOTLIN_METADATA_EXTRA_INT_NAME)
    @Suppress("unchecked_cast")
    return KotlinClassHeader(
        kind = kind.invoke(metadata) as Int?,
        metadataVersion = metadataVersion.invoke(metadata) as IntArray?,
        bytecodeVersion = bytecodeVersion.invoke(metadata) as IntArray?,
        data1 = data1.invoke(metadata) as Array<String>?,
        data2 = data2.invoke(metadata) as Array<String>?,
        extraString = extraString.invoke(metadata) as String?,
        packageName = packageName.invoke(metadata) as String?,
        extraInt = extraInt.invoke(metadata) as Int?
    )
}

private class MetadataWriter(metadata: KotlinClassHeader, visitor: ClassVisitor) : ClassVisitor(ASM7, visitor) {
    private val kotlinMetadata: MutableMap<String, Array<String>> = mutableMapOf(
        KOTLIN_METADATA_DATA_FIELD_NAME to metadata.data1,
        KOTLIN_METADATA_STRINGS_FIELD_NAME to metadata.data2
    )

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val av = super.visitAnnotation(descriptor, visible) ?: return null
        return if (descriptor == KOTLIN_METADATA_DESC) KotlinMetadataWriter(av) else av
    }

    private inner class KotlinMetadataWriter(av: AnnotationVisitor) : AnnotationVisitor(api, av) {
        override fun visitArray(name: String): AnnotationVisitor? {
            val av = super.visitArray(name)
            if (av != null) {
                val data = kotlinMetadata.remove(name) ?: return av
                data.forEach { av.visit(null, it) }
                av.visitEnd()
            }
            return null
        }
    }
}
