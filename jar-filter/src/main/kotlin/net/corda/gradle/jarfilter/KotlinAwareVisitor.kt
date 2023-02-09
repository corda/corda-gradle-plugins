@file:JvmName("KotlinMetadata")
package net.corda.gradle.jarfilter

import kotlinx.metadata.KmClass
import kotlinx.metadata.KmPackage
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.Metadata as JvmMetadata
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor

const val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"
const val KOTLIN_METADATA_DATA_FIELD_NAME = "d1"
const val KOTLIN_METADATA_STRINGS_FIELD_NAME = "d2"
const val KOTLIN_KIND_FIELD_NAME = "k"
const val KOTLIN_METADATA_VERSION_NAME = "mv"
const val KOTLIN_METADATA_EXTRA_INT_NAME = "xi"
const val KOTLIN_METADATA_EXTRA_STRING_NAME = "xs"
const val KOTLIN_METADATA_PACKAGE_NAME= "pn"

/**
 * Kotlin support: Loads the ProtoBuf data from the [kotlin.Metadata] annotation.
 */
abstract class KotlinAwareVisitor(
    api: Int,
    visitor: ClassVisitor,
    @JvmField protected val logger: Logger,
    @JvmField protected val kotlinMetadata: MutableMap<String, Array<String>>
) : ClassVisitor(api, visitor) {

    private var classKind: Int = 0
    private var extraInt: Int? = null
    private var extraString: String? = null
    private var packageName: String? = null
    private var metadataVersion: IntArray? = null

    open val hasUnwantedElements: Boolean get() = kotlinMetadata.isNotEmpty()
    protected open val level: LogLevel = LogLevel.INFO

    protected abstract fun processClassMetadata(kmClass: KmClass): KmClass?
    protected abstract fun processPackageMetadata(kmPackage: KmPackage): KmPackage?
    protected abstract fun processKotlinAnnotation()

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val av = super.visitAnnotation(descriptor, visible) ?: return null
        return if (descriptor == KOTLIN_METADATA_DESC) KotlinMetadataAdaptor(av) else av
    }

    protected fun processMetadata() {
        if (kotlinMetadata.isNotEmpty()) {
            logger.log(level, "- Examining Kotlin @Metadata[k={}]", classKind)
            val data1 = kotlinMetadata.remove(KOTLIN_METADATA_DATA_FIELD_NAME)
            val data2 = kotlinMetadata.remove(KOTLIN_METADATA_STRINGS_FIELD_NAME)
            if (!data1.isNullOrEmpty() && data2 != null) {
                val header = JvmMetadata(
                    classKind,
                    metadataVersion,
                    data1,
                    data2,
                    extraString,
                    packageName,
                    extraInt
                )
                processMetadata(header)?.also {
                    kotlinMetadata[KOTLIN_METADATA_DATA_FIELD_NAME] = it.data1
                    kotlinMetadata[KOTLIN_METADATA_STRINGS_FIELD_NAME] = it.data2
                }
            }
        }
    }

    private fun processClassMetadata(header: Metadata, metadata: KotlinClassMetadata.Class): Metadata? {
        val kmClass = processClassMetadata(metadata.toKmClass()) ?: return null
        return KotlinClassMetadata.writeClass(kmClass, header.metadataVersion, header.extraInt)
            .annotationData
    }

    private fun processFileFacadeMetadata(header: Metadata, metadata: KotlinClassMetadata.FileFacade): Metadata? {
        val kmPackage = processPackageMetadata(metadata.toKmPackage()) ?: return null
        return KotlinClassMetadata.writeFileFacade(kmPackage, header.metadataVersion, header.extraInt)
            .annotationData
    }

    private fun processMultiFileClassPartMetadata(header: Metadata, metadata: KotlinClassMetadata.MultiFileClassPart): Metadata? {
        val kmPackage = processPackageMetadata(metadata.toKmPackage()) ?: return null
        return KotlinClassMetadata.writeMultiFileClassPart(kmPackage, metadata.facadeClassName, header.metadataVersion, header.extraInt)
            .annotationData
    }

    private fun processMetadata(header: Metadata): Metadata? {
        return when (val metadata = KotlinClassMetadata.read(header)) {
            is KotlinClassMetadata.Class -> processClassMetadata(header, metadata)
            is KotlinClassMetadata.FileFacade -> processFileFacadeMetadata(header, metadata)
            is KotlinClassMetadata.MultiFileClassPart -> processMultiFileClassPartMetadata(header, metadata)
            is KotlinClassMetadata.SyntheticClass -> {
                logger.log(level,"-- synthetic class ignored")
                null
            }
            else -> {
                /*
                 * For class-kind=4 (i.e. "multi-file"), we currently
                 * expect data1=[list of multi-file-part classes], data2=null.
                 */
                logger.log(level,"-- unsupported class-kind {}", classKind)
                null
            }
        }
    }

    private inner class KotlinMetadataAdaptor(av: AnnotationVisitor): AnnotationVisitor(api, av) {
        override fun visit(name: String?, value: Any?) {
            when (name) {
                KOTLIN_KIND_FIELD_NAME -> classKind = value as Int
                KOTLIN_METADATA_VERSION_NAME -> metadataVersion = value as IntArray?
                KOTLIN_METADATA_PACKAGE_NAME -> packageName = value as String?
                KOTLIN_METADATA_EXTRA_INT_NAME -> extraInt = value as Int?
                KOTLIN_METADATA_EXTRA_STRING_NAME -> extraString = value as String?
            }
            super.visit(name, value)
        }

        override fun visitArray(name: String): AnnotationVisitor? {
            val av = super.visitArray(name)
            if (av != null) {
                val data = kotlinMetadata.remove(name) ?: return ArrayAccumulator(av, name)
                logger.debug("-- rewrote @Metadata.{}[{}]", name, data.size)
                data.forEach { av.visit(null, it) }
                av.visitEnd()
            }
            return null
        }

        override fun visitEnd() {
            super.visitEnd()
            processKotlinAnnotation()
        }
    }

    private inner class ArrayAccumulator(av: AnnotationVisitor, private val name: String) : AnnotationVisitor(api, av) {
        private val data: MutableList<String> = mutableListOf()

        override fun visit(name: String?, value: Any?) {
            super.visit(name, value)
            data.add(value as String)
        }

        override fun visitEnd() {
            super.visitEnd()
            kotlinMetadata[name] = data.toTypedArray()
            logger.debug("-- read @Metadata.{}[{}]", name, data.size)
        }
    }
}

/**
 * Loads the ProtoBuf data from the [kotlin.Metadata] annotation, or
 * writes new ProtoBuf data that was created during a previous pass.
 */
abstract class KotlinAfterProcessor(
    api: Int,
    visitor: ClassVisitor,
    logger: Logger,
    kotlinMetadata: MutableMap<String, Array<String>>
) : KotlinAwareVisitor(api, visitor, logger, kotlinMetadata) {

    /**
     * Process the metadata once we have finished visiting the class.
     * This will allow us to rewrite the [kotlin.Metadata] annotation
     * in the next visit.
     */
    override fun visitEnd() {
        super.visitEnd()
        processMetadata()
    }

    /**
     * Do nothing immediately after we have parsed [kotlin.Metadata].
     */
    final override fun processKotlinAnnotation() {}
}

/**
 * Loads the ProtoBuf data from the [kotlin.Metadata] annotation
 * and then processes it before visiting the rest of the class.
 */
abstract class KotlinBeforeProcessor(
    api: Int,
    visitor: ClassVisitor,
    logger: Logger,
    kotlinMetadata: MutableMap<String, Array<String>>
) : KotlinAwareVisitor(api, visitor, logger, kotlinMetadata) {

    /**
     * Process the ProtoBuf data as soon as we have parsed [kotlin.Metadata].
     */
    final override fun processKotlinAnnotation() = processMetadata()
}
