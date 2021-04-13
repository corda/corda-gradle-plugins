package net.corda.gradle.jarfilter

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag.Constructor.IS_SECONDARY
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmDeclarationContainer
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmPackage
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmTypeAlias
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.jvm.syntheticMethodForAnnotations
import org.gradle.api.logging.Logger

/**
 * Base class for removing unwanted elements from [kotlin.Metadata] annotations.
 * This is used by [FilterTransformer] for [JarFilterTask].
 */
abstract class MetadataTransformer<out T : KmDeclarationContainer>(
    @JvmField protected val logger: Logger,
    private val deletedFields: Collection<FieldElement>,
    private val deletedFunctions: Collection<MethodElement>,
    @JvmField protected val handleExtraMethod: (MethodElement) -> Unit,
    @JvmField protected val handleExtraField: (FieldElement) -> Unit,
    @JvmField protected val handleSameAs: MethodElement.(MethodElement) -> Unit,
    @JvmField protected val metadata: T
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

                // The synthetic `$default` method may not have been annotated,
                // so ensure we handle it in the same way as its "host" method.
                synthetic.handleSameAs(method)
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

    protected fun filterProperties(): Int
        = ArrayList(deletedFields).count(::filterProperty) + filterProperties(HashSet(deletedFunctions))

    private fun filterProperty(deleted: FieldElement): Boolean {
        val annotatedMethod = deleted.asKotlinAnnotationsMethod()
        return if (annotatedMethod != null) {
            /*
             * The "delete" annotation has been applied to this
             * property's synthetic "annotation holder" method.
             */
            filterPropertyByAnnotatedMethod(deleted, annotatedMethod)
        } else {
            /*
             * The property's underlying field was annotated.
             */
            filterPropertyByField(deleted)
        }
    }

    private fun filterPropertyByAnnotatedMethod(deleted: FieldElement, annotatedMethod: MethodElement): Boolean {
        for (idx in 0 until properties.size) {
            val property = properties[idx]
            val syntheticMethod = property.syntheticMethodForAnnotations ?: continue
            if (annotatedMethod.name == syntheticMethod.name && annotatedMethod.descriptor == syntheticMethod.desc) {
                /*
                 * Ensure that the accessor functions and the underlying field
                 * are deleted along with the synthetic annotation-holder.
                 */
                property.fieldSignature?.apply {
                    deleteExtra(toFieldElement())
                }
                deleteAccessorsFor(property)

                if (deleted.extension == "()") {
                    /*
                     * Kotlin 1.4 has renamed the synthetic annotation-holder
                     * method to use the name of the property's getter rather
                     * than the property itself. However, the logic for also
                     * deleting any synthetic inner classes it may have depends
                     * on knowing the name of the deleted property. So inject
                     * a "fake" deleted reference to the pre-Kotlin 1.4
                     * annotation-holder method into the working data.
                     */
                    handleExtraMethod(deleted.asKotlinAnnotationsMethod(property.name))
                }

                logger.info("-- removing property: {},{}", property.name, deleted.descriptor)
                properties.removeAt(idx)
                return true
            }
        }
        return false
    }

    private fun filterPropertyByField(deleted: FieldElement): Boolean {
        for (idx in 0 until properties.size) {
            val property = properties[idx]
            if (property.name == deleted.name) {
                /*
                 * We already know about the underlying field,
                 * so we just need to delete the accessors here.
                 */
                deleteAccessorsFor(property)

                /*
                 * Inject a "fake" deleted method reference to the pre-Kotlin 1.4
                 * annotation-holder into the working data, for the sake of the
                 * inner class logic (see comment above).
                 */
                handleExtraMethod(deleted.asKotlinAnnotationsMethod(property.name))

                logger.info("-- removing property: {},{}", property.name, deleted.descriptor)
                properties.removeAt(idx)
                return true
            }
        }
        return false
    }

    private fun deleteAccessorsFor(property: KmProperty) {
        property.getterSignature?.apply {
            deleteExtra(toMethodElement())
        }
        property.setterSignature?.apply {
            deleteExtra(toMethodElement())
        }
    }

    private fun deleteExtra(func: MethodElement) {
        if (!deletedFunctions.contains(func)) {
            logger.info("-- identified extra method {} for deletion", func.signature)
            handleExtraMethod(func)
            filterFunction(func)
        }
    }

    private fun deleteExtra(field: FieldElement) {
        if (!deletedFields.contains(field)) {
            logger.info("-- identified extra field {},{} for deletion", field.name, field.descriptor)
            handleExtraField(field)
        }
    }

    /**
     * Some properties have no backing field to be deleted, which means we cannot
     * detect them by examining deleted [FieldElement]s. So instead filter out all
     * such properties which have also lost all of their accessor functions.
     */
    private fun filterProperties(deleted: Set<MethodElement>): Int {
        var deleteCount = 0
        var idx = 0
        while (idx < properties.size) {
            val property = properties[idx]
            if (property.fieldSignature == null) {
                val getter = property.getterSignature
                val setter = property.setterSignature

                /**
                 * Both `val` and `var` properties have a "getter" function,
                 * but only `var` properties also have a "setter".
                 */
                if ((getter != null && deleted.contains(getter.toMethodElement()))
                        && (setter == null || deleted.contains(setter.toMethodElement()))) {
                    logger.info("-- removing property: {} (all accessors deleted)", property.name)
                    properties.removeAt(idx)
                    ++deleteCount
                    continue
                }
            }
            ++idx
        }
        return deleteCount
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
    handleExtraField: (FieldElement) -> Unit,
    handleSameAs: MethodElement.(MethodElement) -> Unit,
    kmClass: KmClass
) : MetadataTransformer<KmClass>(
    logger,
    deletedFields,
    deletedFunctions,
    handleExtraMethod,
    handleExtraField,
    handleSameAs,
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
                synthetic.handleSameAs(method)
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
                if (IS_SECONDARY(constructor.flags)) {
                    logger.info("-- removing constructor: {}", deleted.signature)
                } else {
                    logger.warn("Removing primary constructor: {}{}", className, deleted.descriptor)
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
    handleExtraField: (FieldElement) -> Unit,
    handleSameAs: MethodElement.(MethodElement) -> Unit,
    kmPackage: KmPackage
) : MetadataTransformer<KmPackage>(
    logger,
    deletedFields,
    deletedFunctions,
    handleExtraMethod,
    handleExtraField,
    handleSameAs,
    metadata = kmPackage
) {
    override fun filter(): Int = (
        filterProperties()
        + filterFunctions()
        + filterTypeAliases()
    )
}
