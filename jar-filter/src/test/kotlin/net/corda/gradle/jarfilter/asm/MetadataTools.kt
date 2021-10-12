@file:JvmName("MetadataTools")
package net.corda.gradle.jarfilter.asm

import kotlinx.metadata.InconsistentKotlinMetadataException
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import net.corda.gradle.jarfilter.ASM_API
import net.corda.gradle.jarfilter.KOTLIN_METADATA_DATA_FIELD_NAME
import net.corda.gradle.jarfilter.KOTLIN_METADATA_DESC
import net.corda.gradle.jarfilter.KOTLIN_METADATA_STRINGS_FIELD_NAME
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor

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
val Class<*>.fileMetadata: FileMetadata get() {
    val metadata = (KotlinClassMetadata.read(readMetadata()) as? KotlinClassMetadata.FileFacade
        ?: throw InconsistentKotlinMetadataException("Unknown metadata format")).toKmPackage()
    return FileMetadata(metadata)
}

/**
 * For accessing the parts of class metadata that Kotlin reflection cannot reach.
 */
val Class<*>.classMetadata: ClassMetadata get() {
    val metadata = (KotlinClassMetadata.read(readMetadata()) as? KotlinClassMetadata.Class
        ?: throw InconsistentKotlinMetadataException("Unknown metadata format")).toKmClass()
    return ClassMetadata(metadata)
}

private fun Class<*>.readMetadata(): KotlinClassHeader {
    val metadata = getAnnotation(Metadata::class.java)
    return KotlinClassHeader(
        kind = metadata.kind,
        metadataVersion = metadata.metadataVersion,
        data1 = metadata.data1,
        data2 = metadata.data2,
        extraString = metadata.extraString,
        packageName = metadata.packageName,
        extraInt = metadata.extraInt
    )
}

private class MetadataWriter(metadata: KotlinClassHeader, visitor: ClassVisitor) : ClassVisitor(ASM_API, visitor) {
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
            super.visitArray(name)?.also { av ->
                val data = kotlinMetadata.remove(name) ?: return av
                data.forEach { av.visit(null, it) }
                av.visitEnd()
            }
            return null
        }
    }
}
