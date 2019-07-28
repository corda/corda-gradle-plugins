package net.corda.gradle.jarfilter

import kotlinx.metadata.*
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.signature
import org.gradle.api.logging.Logger

/**
 * Base class for aligning the contents of [kotlin.Metadata] annotations
 * with the contents of the host byte-code.
 * This is used by [MetaFixerVisitor] for [MetaFixerTask].
 */
abstract class MetaFixerTransformer<out T : KmDeclarationContainer>(
    protected val logger: Logger,
    private val actualFields: Collection<FieldElement>,
    protected val actualMethods: Collection<String>,
    protected val metadata: T
) {
    private val properties: MutableList<KmProperty> = metadata.properties
    private val functions: MutableList<KmFunction> = metadata.functions

    protected open val classDescriptor: ClassName = ""

    protected abstract fun filter(): Int

    protected fun filterFunctions(): Int {
        var count = 0
        var idx = 0
        removed@ while (idx < functions.size) {
            val function = functions[idx]
            val signature = function.signature?.asString()
            if (signature != null) {
                if (!actualMethods.contains(signature)) {
                    logger.info("-- removing method: {}", signature)
                    functions.removeAt(idx)
                    ++count
                    continue@removed
                } else if (function.valueParameters.hasAnyDefaultValues
                             && !actualMethods.contains(signature.toKotlinDefaultFunction(classDescriptor))) {
                    logger.info("-- removing default parameter values: {}", signature)
                    function.valueParameters.forEach { it.clearDeclaresDefaultValue() }
                    ++count
                }
            }
            ++idx
        }
        return count
    }

    protected fun filterProperties(): Int {
        var count = 0
        var idx = 0
        removed@ while (idx < properties.size) {
            val property = properties[idx]
            val field = property.fieldSignature
            val getterMethod = property.getterSignature

            /**
             * A property annotated with [JvmField] will use a field instead of a getter method.
             * But properties without [JvmField] will also usually have a backing field. So we only
             * remove a property that has either lost its getter method, or never had a getter method
             * and has lost its field.
             *
             * Having said that, we cannot remove [JvmField] properties from a companion object class
             * because these properties are implemented as static fields on the companion's host class.
             */
            val isValidProperty = if (getterMethod == null) {
                (field == null) || actualFields.contains(field.toFieldElement()) || (metadata is KmClass && Flag.Class.IS_COMPANION_OBJECT(metadata.flags))
            } else {
                actualMethods.contains(getterMethod.asString())
            }

            if (!isValidProperty) {
                logger.info("-- removing property: {},{}", property.name, field?.desc ?: getterMethod?.desc)
                properties.removeAt(idx)
                ++count
                continue@removed
            }
            ++idx
        }
        return count
    }

    fun transform(): T? {
        return if (filter() == 0) {
            null
        } else {
            metadata
        }
    }
}

/**
 * Aligns a [kotlin.Metadata] annotation containing a [KmClass] object
 * in its [data1][kotlin.Metadata.data1] field with the byte-code of its host class.
 */
class ClassMetaFixerTransformer(
    logger: Logger,
    actualFields: Collection<FieldElement>,
    actualMethods: Collection<String>,
    private val actualNestedClasses: Collection<String>,
    private val actualClasses: Collection<String>,
    kmClass: KmClass
) : MetaFixerTransformer<KmClass>(
    logger,
    actualFields,
    actualMethods,
    kmClass
) {
    override val classDescriptor = "L${kmClass.name.toInternalName()};"
    private val constructors = kmClass.constructors
    private val nestedClassNames = kmClass.nestedClasses
    private val sealedSubclassNames= kmClass.sealedSubclasses

    override fun filter(): Int {
        var count = filterProperties() + filterFunctions() + filterNestedClasses() + filterSealedSubclassNames()
        if (!Flag.Class.IS_ANNOTATION_CLASS(metadata.flags)) {
            count += filterConstructors()
        }
        return count
    }

    private fun filterConstructors(): Int {
        var count = 0
        var idx = 0
        removed@ while (idx < constructors.size) {
            val constructor = constructors[idx]
            val signature = constructor.signature?.asString()
            if (signature != null) {
                if (!actualMethods.contains(signature)) {
                    logger.info("-- removing constructor: {}", signature)
                    constructors.removeAt(idx)
                    ++count
                    continue@removed
                } else if (constructor.valueParameters.hasAnyDefaultValues
                        && !actualMethods.contains(signature.toKotlinDefaultConstructor())) {
                    logger.info("-- removing default parameter values: {}", signature)
                    constructor.valueParameters.forEach { it.clearDeclaresDefaultValue() }
                    ++count
                }
            }
            ++idx
        }
        return count
    }

    private fun filterNestedClasses(): Int {
        var count = 0
        var idx = 0
        while (idx < nestedClassNames.size) {
            val nestedClassName = nestedClassNames[idx]
            if (actualNestedClasses.contains(nestedClassName)) {
                ++idx
            } else {
                logger.info("-- removing nested class: {}", nestedClassName)
                nestedClassNames.removeAt(idx)
                ++count
            }
        }
        return count
    }

    private fun filterSealedSubclassNames(): Int {
        var count = 0
        var idx = 0
        while (idx < sealedSubclassNames.size) {
            val sealedSubclassName = sealedSubclassNames[idx].toInternalName()
            if (actualClasses.contains(sealedSubclassName)) {
                ++idx
            } else {
                logger.info("-- removing sealed subclass: {}", sealedSubclassName)
                sealedSubclassNames.removeAt(idx)
                ++count
            }
        }
        return count
    }
}

/**
 * Aligns a [kotlin.Metadata] annotation containing a [KmPackage] object
 * in its [data1][kotlin.Metadata.data1] field with the byte-code of its host class.
 */
class PackageMetaFixerTransformer(
    logger: Logger,
    actualFields: Collection<FieldElement>,
    actualMethods: Collection<String>,
    kmPackage: KmPackage
) : MetaFixerTransformer<KmPackage>(
    logger,
    actualFields,
    actualMethods,
    kmPackage
) {
    override fun filter(): Int = filterProperties() + filterFunctions()
}
