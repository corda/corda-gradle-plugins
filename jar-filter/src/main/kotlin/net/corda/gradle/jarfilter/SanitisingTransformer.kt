package net.corda.gradle.jarfilter

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag.Constructor.IS_SECONDARY
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmPackage
import kotlinx.metadata.jvm.signature
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * This is (hopefully?!) a temporary solution for classes with [JvmOverloads] constructors.
 * We need to be able to annotate ONLY the secondary constructors for such classes, but Kotlin
 * will apply any annotation to all constructors equally. Nor can we replace the overloaded
 * constructor with individual constructors because this will break ABI compatibility. (Kotlin
 * generates a synthetic public constructor to handle default parameter values.)
 *
 * This transformer identifies a class's primary constructor and removes all of its unwanted annotations.
 * It will become superfluous when Kotlin allows us to target only the secondary constructors with our
 * filtering annotations in the first place.
 */
class SanitisingTransformer(
    visitor: ClassVisitor,
    logger: Logger,
    private val unwantedAnnotations: Set<String>,
    private val syntheticMethods: UnwantedMap
) : KotlinBeforeProcessor(ASM_API, visitor, logger, mutableMapOf()) {

    var isModified: Boolean = false
        private set
    override val level: LogLevel = LogLevel.DEBUG

    private var className: ClassName = "(unknown)"
    private var primaryConstructor: MethodElement? = null
    private var hasDefaultValues: Boolean = false

    override fun processPackageMetadata(kmPackage: KmPackage): KmPackage? = null

    override fun processClassMetadata(kmClass: KmClass): KmClass? {
        for (constructor in kmClass.constructors) {
            if (!IS_SECONDARY(constructor.flags)) {
                val signature = constructor.signature ?: break
                primaryConstructor = signature.toMethodElement()
                hasDefaultValues = constructor.valueParameters.hasAnyDefaultValues
                logger.log(level, "Class {} has primary constructor {}", className, signature.asString())
                break
            }
        }
        return null
    }

    override fun visit(version: Int, access: Int, clsName: String, signature: String?, superName: String?, interfaces: Array<String>?) {
        className = clsName
        super.visit(version, access, clsName, signature, superName, interfaces)
    }

    override fun visitMethod(access: Int, methodName: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
        val method = MethodElement(methodName, descriptor, access)
        val mv = super.visitMethod(access, methodName, descriptor, signature, exceptions) ?: return null
        return if (method == primaryConstructor) SanitisingMethodAdapter(mv, method) else mv
    }

    private inner class SanitisingMethodAdapter(mv: MethodVisitor, private val method: MethodElement) : MethodVisitor(api, mv) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (unwantedAnnotations.contains(descriptor)) {
                logger.info("Sanitising annotation {} from method {}.{}{}", descriptor, className, method.name, method.descriptor)

                /*
                 * As of Kotlin 1.3.40, we can no longer assume that the synthetic constructors
                 * generated to support default parameter values will receive user annotations.
                 * We must record what we would expect those annotations to have been.
                 */
                if (hasDefaultValues) {
                    method.asKotlinDefaultConstructor()?.also { syn ->
                        logger.info("- applying {} to synthetic {}{}", descriptor, syn.name, syn.descriptor)
                        syntheticMethods.computeIfAbsent(className) { mutableListOf() }
                            .add(descriptor to syn)
                    }
                }

                isModified = true
                return null
            }
            return super.visitAnnotation(descriptor, visible)
        }
    }
}
