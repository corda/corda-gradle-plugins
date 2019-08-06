package net.corda.gradle.jarfilter

import kotlinx.metadata.*
import kotlinx.metadata.Flag.Constructor.IS_PRIMARY
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.jvm.signature
import org.gradle.api.logging.Logger

/**
 * Base class for removing unwanted elements from [kotlin.Metadata] annotations.
 * This is used by [FilterTransformer] for [JarFilterTask].
 */
abstract class MetadataTransformer<out T : KmDeclarationContainer>(
    protected val logger: Logger,
    private val deletedFields: Collection<FieldElement>,
    private val deletedFunctions: Collection<MethodElement>,
    protected val handleExtraMethod: (MethodElement) -> Unit,
    protected val handleSyntheticMethod: (MethodElement, MethodElement) -> Unit,
    protected val metadata: T
) {
    private val functions: MutableList<KmFunction> = metadata.functions
    private val properties: MutableList<KmProperty> = metadata.properties
    private val typeAliases: MutableList<KmTypeAlias> = metadata.typeAliases

    protected open val classDescriptor: ClassName = ""

    protected abstract fun filter(): Int

    fun transform(): T? {
        doBeforeFiltering()
        return if (filter() == 0) {
            null
        } else {
            metadata
        }
    }

    protected open fun doBeforeFiltering() {
        for (function in functions) {
            if (function.valueParameters.hasAnyDefaultValues) {
                val method = function.signature?.toMethodElement() ?: continue
                val synthetic = method.asKotlinDefaultFunction(classDescriptor) ?: continue

                // The synthetic "$default" method may not have been annotated,
                // so ensure we handle it in the same way as its "host" method.
                handleSyntheticMethod(synthetic, method)
            }
        }
    }

    protected fun filterFunctions(): Int = deletedFunctions.count(::filterFunction)

    private fun filterFunction(deleted: MethodElement): Boolean {
        for (idx in 0 until functions.size) {
            val function = functions[idx]
            if (function.name == deleted.name) {
                val signature = function.signature?.toMethodElement()
                if (signature == deleted) {
                    logger.info("-- removing function: {}", deleted.signature)
                    functions.removeAt(idx)
                    return true
                }
            }
        }
        return false
    }

    protected fun filterProperties(): Int = deletedFields.count(::filterProperty)

    private fun filterProperty(deleted: FieldElement): Boolean {
        for (idx in 0 until properties.size) {
            val property = properties[idx]
            if (property.name == deleted.name) {
                // Check that this property's getter has the correct descriptor.
                // If it doesn't then we have the wrong property here.
                val getter = property.getterSignature
                if (getter != null) {
                    if (!getter.desc.startsWith(deleted.extension)) {
                        continue
                    }
                    deleteExtra(getter.toMethodElement())
                }
                property.setterSignature?.run {
                    deleteExtra(toMethodElement())
                }

                logger.info("-- removing property: {},{}", deleted.name, deleted.descriptor)
                properties.removeAt(idx)
                return true
            }
        }
        return false
    }

    private fun deleteExtra(func: MethodElement) {
        if (!deletedFunctions.contains(func)) {
            logger.info("-- identified extra method {} for deletion", func.signature)
            handleExtraMethod(func)
            filterFunction(func)
        }
    }

    protected fun filterTypeAliases(): Int {
        if (deletedFields.isEmpty()) return 0

        var count = 0
        var idx = 0
        while (idx < typeAliases.size) {
            val aliasName = typeAliases[idx].name
            if (deletedFields.any { it.name == aliasName && it.extension == "()" }) {
                logger.info("-- removing typealias: {}", aliasName)
                typeAliases.removeAt(idx)
                ++count
            } else {
                ++idx
            }
        }
        return count
    }
}

/**
 * Removes elements from a [kotlin.Metadata] annotation that contains
 * a [KmClass] object in its [data1][kotlin.Metadata.data1] field.
 */
class ClassMetadataTransformer(
    logger: Logger,
    deletedFields: Collection<FieldElement>,
    deletedFunctions: Collection<MethodElement>,
    private val deletedConstructors: Collection<MethodElement>,
    private val deletedNestedClasses: Collection<String>,
    private val deletedClasses: Collection<String>,
    handleExtraMethod: (MethodElement) -> Unit,
    handleSyntheticMethod: (MethodElement, MethodElement) -> Unit,
    kmClass: KmClass
) : MetadataTransformer<KmClass>(
    logger,
    deletedFields,
    deletedFunctions,
    handleExtraMethod,
    handleSyntheticMethod,
    metadata = kmClass
) {
    private val className = kmClass.name
    private val nestedClassNames = kmClass.nestedClasses
    private val sealedSubclassNames = kmClass.sealedSubclasses
    private val constructors = kmClass.constructors

    override val classDescriptor = "L${className.toInternalName()};"

    override fun doBeforeFiltering() {
        super.doBeforeFiltering()
        for (constructor in constructors) {
            if (constructor.valueParameters.hasAnyDefaultValues) {
                val method = constructor.signature?.toMethodElement() ?: continue
                val synthetic = method.asKotlinDefaultConstructor() ?: continue

                // The synthetic "default values" constructor may not have been annotated,
                // so ensure we handle it in the same way as its "host" method.
                handleSyntheticMethod(synthetic, method)
            }
        }
    }

    override fun filter(): Int = (
        filterProperties()
        + filterFunctions()
        + filterConstructors()
        + filterNestedClasses()
        + filterTypeAliases()
        + filterSealedSubclasses()
    )

    private fun filterConstructors(): Int = deletedConstructors.count(::filterConstructor)

    private fun filterConstructor(deleted: MethodElement): Boolean {
        /*
         * Constructors with the default parameter marker are synthetic and DO NOT have
         * entries in the metadata. So we construct an element for the "primary" one
         * that it was synthesised for, and which we DO expect to find.
         */
        val deletedPrimary = deleted.asKotlinNonDefaultConstructor()

        for (idx in 0 until constructors.size) {
            val constructor = constructors[idx]
            val signature = (constructor.signature ?: continue).toMethodElement()
            if (signature == deleted) {
                if (IS_PRIMARY(constructor.flags)) {
                    logger.warn("Removing primary constructor: {}{}", className, deleted.descriptor)
                } else {
                    logger.info("-- removing constructor: {}", deleted.signature)
                }
                constructors.removeAt(idx)
                return true
            } else if (signature == deletedPrimary) {
                constructor.valueParameters.forEach { value ->
                    value.clearDeclaresDefaultValue()
                }
                logger.info("-- removing default parameter values: {}", deletedPrimary.signature)
                return true
            }
        }
        return false
    }

    private fun filterNestedClasses(): Int {
        if (deletedNestedClasses.isEmpty()) return 0

        var count = 0
        var idx = 0
        while (idx < nestedClassNames.size) {
            val nestedClassName = nestedClassNames[idx]
            if (deletedNestedClasses.contains(nestedClassName)) {
                logger.info("-- removing nested class: {}", nestedClassName)
                nestedClassNames.removeAt(idx)
                ++count
            } else {
                ++idx
            }
        }
        return count
    }


    private fun filterSealedSubclasses(): Int {
        if (deletedClasses.isEmpty()) return 0

        var count = 0
        var idx = 0
        while (idx < sealedSubclassNames.size) {
            val subclassName = sealedSubclassNames[idx].toInternalName()
            if (deletedClasses.contains(subclassName)) {
                logger.info("-- removing sealed subclass: {}", subclassName)
                sealedSubclassNames.removeAt(idx)
                ++count
            } else {
                ++idx
            }
        }
        return count
    }
}

/**
 * Removes elements from a [kotlin.Metadata] annotation that contains
 * a [KmPackage] object in its [data1][kotlin.Metadata.data1] field.
 */
class PackageMetadataTransformer(
    logger: Logger,
    deletedFields: Collection<FieldElement>,
    deletedFunctions: Collection<MethodElement>,
    handleExtraMethod: (MethodElement) -> Unit,
    handleSyntheticMethod: (MethodElement, MethodElement) -> Unit,
    kmPackage: KmPackage
) : MetadataTransformer<KmPackage>(
    logger,
    deletedFields,
    deletedFunctions,
    handleExtraMethod,
    handleSyntheticMethod,
    metadata = kmPackage
) {
    override fun filter(): Int = (
        filterProperties()
        + filterFunctions()
        + filterTypeAliases()
    )
}
