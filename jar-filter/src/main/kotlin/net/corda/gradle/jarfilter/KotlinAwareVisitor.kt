@file:JvmName("KotlinMetadata")
package net.corda.gradle.jarfilter

import kotlinx.metadata.jvm.KotlinClassHeader
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor

const val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"
const val KOTLIN_METADATA_DATA_FIELD_NAME = "d1"
const val KOTLIN_METADATA_STRINGS_FIELD_NAME = "d2"
const val KOTLIN_KIND_FIELD_NAME = "k"

private const val KOTLIN_CLASS: Int = KotlinClassHeader.CLASS_KIND
private const val KOTLIN_FILE: Int = KotlinClassHeader.FILE_FACADE_KIND
private const val KOTLIN_SYNTHETIC: Int = KotlinClassHeader.SYNTHETIC_CLASS_KIND
private const val KOTLIN_MULTIFILE_PART: Int = KotlinClassHeader.MULTI_FILE_CLASS_PART_KIND

/**
 * Kotlin support: Loads the ProtoBuf data from the [kotlin.Metadata] annotation.
 */
abstract class KotlinAwareVisitor(
    api: Int,
    visitor: ClassVisitor,
    protected val logger: Logger,
    protected val kotlinMetadata: MutableMap<String, List<String>>
) : ClassVisitor(api, visitor) {

    private var classKind: Int = 0

    open val hasUnwantedElements: Boolean get() = kotlinMetadata.isNotEmpty()
    protected open val level: LogLevel = LogLevel.INFO

    protected abstract fun processClassMetadata(d1: List<String>, d2: List<String>): List<String>
    protected abstract fun processPackageMetadata(d1: List<String>, d2: List<String>): List<String>
    protected abstract fun processKotlinAnnotation()

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val av = super.visitAnnotation(descriptor, visible) ?: return null
        return if (descriptor == KOTLIN_METADATA_DESC) KotlinMetadataAdaptor(av) else av
    }

    protected fun processMetadata() {
        if (kotlinMetadata.isNotEmpty()) {
            logger.log(level, "- Examining Kotlin @Metadata[k={}]", classKind)
            val d1 = kotlinMetadata.remove(KOTLIN_METADATA_DATA_FIELD_NAME)
            val d2 = kotlinMetadata.remove(KOTLIN_METADATA_STRINGS_FIELD_NAME)
            if (d1 != null && d1.isNotEmpty() && d2 != null) {
                processMetadata(d1, d2).apply {
                    if (isNotEmpty()) {
                        kotlinMetadata[KOTLIN_METADATA_DATA_FIELD_NAME] = this
                        kotlinMetadata[KOTLIN_METADATA_STRINGS_FIELD_NAME] = d2
                    }
                }
            }
        }
    }

    private fun processMetadata(d1: List<String>, d2: List<String>): List<String> {
        return when (classKind) {
            KOTLIN_CLASS -> processClassMetadata(d1, d2)
            KOTLIN_FILE, KOTLIN_MULTIFILE_PART -> processPackageMetadata(d1, d2)
            KOTLIN_SYNTHETIC -> {
                logger.log(level,"-- synthetic class ignored")
                emptyList()
            }
            else -> {
                /*
                 * For class-kind=4 (i.e. "multi-file"), we currently
                 * expect d1=[list of multi-file-part classes], d2=null.
                 */
                logger.log(level,"-- unsupported class-kind {}", classKind)
                emptyList()
            }
        }
    }

    private inner class KotlinMetadataAdaptor(av: AnnotationVisitor): AnnotationVisitor(api, av) {
        override fun visit(name: String?, value: Any?) {
            if (name == KOTLIN_KIND_FIELD_NAME) {
                classKind = value as Int
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
            kotlinMetadata[name] = data
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
    kotlinMetadata: MutableMap<String, List<String>>
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
    kotlinMetadata: MutableMap<String, List<String>>
) : KotlinAwareVisitor(api, visitor, logger, kotlinMetadata) {

    /**
     * Process the ProtoBuf data as soon as we have parsed [kotlin.Metadata].
     */
    final override fun processKotlinAnnotation() = processMetadata()
}
