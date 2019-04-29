package net.corda.gradle.jarfilter

import kotlinx.metadata.internal.metadata.ProtoBuf
import kotlinx.metadata.internal.metadata.ProtoBuf.Class.Kind.*
import kotlinx.metadata.internal.metadata.deserialization.Flags.*
import kotlinx.metadata.internal.metadata.deserialization.NameResolver
import kotlinx.metadata.internal.metadata.deserialization.TypeTable
import kotlinx.metadata.internal.metadata.jvm.JvmProtoBuf.*
import kotlinx.metadata.internal.metadata.jvm.deserialization.BitEncoding
import kotlinx.metadata.internal.metadata.jvm.deserialization.JvmNameResolver
import kotlinx.metadata.internal.metadata.jvm.deserialization.JvmProtoBufUtil
import kotlinx.metadata.internal.metadata.jvm.deserialization.JvmProtoBufUtil.EXTENSION_REGISTRY
import kotlinx.metadata.internal.protobuf.ExtensionRegistryLite
import kotlinx.metadata.internal.protobuf.MessageLite
import org.gradle.api.logging.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Base class for aligning the contents of [kotlin.Metadata] annotations
 * with the contents of the host byte-code.
 * This is used by [MetaFixerVisitor] for [MetaFixerTask].
 */
internal abstract class MetaFixerTransformer<out T : MessageLite>(
    private val logger: Logger,
    private val actualFields: Collection<FieldElement>,
    private val actualMethods: Collection<String>,
    private val actualNestedClasses: Collection<String>,
    private val actualClasses: Collection<String>,
    data1: List<String>,
    data2: List<String>,
    parser: (InputStream, ExtensionRegistryLite) -> T
) {
    private val stringTableTypes: StringTableTypes
    protected val nameResolver: NameResolver
    protected val message: T

    protected abstract val typeTable: TypeTable
    protected open val classDescriptor: String = ""
    protected open val classKind: ProtoBuf.Class.Kind? = null
    protected abstract val properties: MutableList<ProtoBuf.Property>
    protected abstract val functions: MutableList<ProtoBuf.Function>
    protected abstract val constructors: MutableList<ProtoBuf.Constructor>
    protected open val nestedClassNames: MutableList<Int> get() = throw UnsupportedOperationException("No nestedClassNames")
    protected open val sealedSubclassNames: MutableList<Int> get() = throw UnsupportedOperationException("No sealedSubclassNames")

    init {
        val input = ByteArrayInputStream(BitEncoding.decodeBytes(data1.toTypedArray()))
        stringTableTypes = StringTableTypes.parseDelimitedFrom(input, EXTENSION_REGISTRY)
        nameResolver = JvmNameResolver(stringTableTypes, data2.toTypedArray())
        message = parser(input, EXTENSION_REGISTRY)
    }

    abstract fun rebuild(): T

    private fun filterNestedClasses(): Int {
        if (classKind == null) return 0

        var count = 0
        var idx = 0
        while (idx < nestedClassNames.size) {
            val nestedClassName = nameResolver.getString(nestedClassNames[idx])
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
        if (classKind == null) return 0

        var count = 0
        var idx = 0
        while (idx < sealedSubclassNames.size) {
            val sealedSubclassName = nameResolver.getClassInternalName(sealedSubclassNames[idx])
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

    private fun filterFunctions(): Int {
        var count = 0
        var idx = 0
        removed@ while (idx < functions.size) {
            val function = functions[idx]
            val signature = JvmProtoBufUtil.getJvmMethodSignature(function, nameResolver, typeTable)?.asString()
            if (signature != null) {
                if (!actualMethods.contains(signature)) {
                    logger.info("-- removing method: {}", signature)
                    functions.removeAt(idx)
                    ++count
                    continue@removed
                } else if (function.valueParameterList.hasAnyDefaultValues
                             && !actualMethods.contains(signature.toKotlinDefaultFunction(classDescriptor))) {
                    logger.info("-- removing default parameter values: {}", signature)
                    functions[idx] = function.toBuilder()
                        .updateValueParameters(ProtoBuf.ValueParameter::clearDeclaresDefaultValue)
                        .build()
                    ++count
                }
            }
            ++idx
        }
        return count
    }

    private fun filterConstructors(): Int {
        var count = 0
        var idx = 0
        removed@ while (idx < constructors.size) {
            val constructor = constructors[idx]
            val signature = JvmProtoBufUtil.getJvmConstructorSignature(constructor, nameResolver, typeTable)?.asString()
            if (signature != null) {
                if (!actualMethods.contains(signature)) {
                    logger.info("-- removing constructor: {}", signature)
                    constructors.removeAt(idx)
                    ++count
                    continue@removed
                } else if (constructor.valueParameterList.hasAnyDefaultValues
                             && !actualMethods.contains(signature.toKotlinDefaultConstructor())) {
                    logger.info("-- removing default parameter values: {}", signature)
                    constructors[idx] = constructor.toBuilder()
                        .updateValueParameters(ProtoBuf.ValueParameter::clearDeclaresDefaultValue)
                        .build()
                    ++count
                }
            }
            ++idx
        }
        return count
    }

    private fun filterProperties(): Int {
        var count = 0
        var idx = 0
        removed@ while (idx < properties.size) {
            val property = properties[idx]
            val signature = property.getExtensionOrNull(propertySignature)
            if (signature != null) {
                val field = signature.toFieldElement(property, nameResolver, typeTable)
                val getterMethod = signature.toGetter(nameResolver)

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
                    actualFields.contains(field) || classKind == COMPANION_OBJECT
                } else {
                    actualMethods.contains(getterMethod.signature)
                }

                if (!isValidProperty) {
                    logger.info("-- removing property: {},{}", field.name, field.descriptor)
                    properties.removeAt(idx)
                    ++count
                    continue@removed
                }
            }
            ++idx
        }
        return count
    }

    fun transform(): List<String> {
        var count = filterProperties() + filterFunctions() + filterNestedClasses() + filterSealedSubclassNames()
        if (classKind != ANNOTATION_CLASS) {
            count += filterConstructors()
        }
        if (count == 0) {
            return emptyList()
        }

        val bytes = ByteArrayOutputStream()
        stringTableTypes.writeDelimitedTo(bytes)
        rebuild().writeTo(bytes)
        return BitEncoding.encodeBytes(bytes.toByteArray()).toList()
    }
}

/**
 * Aligns a [kotlin.Metadata] annotation containing a [ProtoBuf.Class] object
 * in its [data1][kotlin.Metadata.data1] field with the byte-code of its host class.
 */
internal class ClassMetaFixerTransformer(
    logger: Logger,
    actualFields: Collection<FieldElement>,
    actualMethods: Collection<String>,
    actualNestedClasses: Collection<String>,
    actualClasses: Collection<String>,
    data1: List<String>,
    data2: List<String>
) : MetaFixerTransformer<ProtoBuf.Class>(
    logger,
    actualFields,
    actualMethods,
    actualNestedClasses,
    actualClasses,
    data1,
    data2,
    ProtoBuf.Class::parseFrom
) {
    override val typeTable = TypeTable(message.typeTable)
    override val classDescriptor = "L${nameResolver.getClassInternalName(message.fqName)};"
    override val classKind: ProtoBuf.Class.Kind = CLASS_KIND.get(message.flags)
    override val properties = mutableList(message.propertyList)
    override val functions = mutableList(message.functionList)
    override val constructors = mutableList(message.constructorList)
    override val nestedClassNames = mutableList(message.nestedClassNameList)
    override val sealedSubclassNames= mutableList(message.sealedSubclassFqNameList)

    override fun rebuild(): ProtoBuf.Class = message.toBuilder().apply {
        clearConstructor().addAllConstructor(constructors)
        clearFunction().addAllFunction(functions)

        if (nestedClassNames.size != nestedClassNameCount) {
            clearNestedClassName().addAllNestedClassName(nestedClassNames)
        }
        if (sealedSubclassNames.size != sealedSubclassFqNameCount) {
            clearSealedSubclassFqName().addAllSealedSubclassFqName(sealedSubclassNames)
        }
        if (properties.size != propertyCount) {
            clearProperty().addAllProperty(properties)
        }
    }.build()
}

/**
 * Aligns a [kotlin.Metadata] annotation containing a [ProtoBuf.Package] object
 * in its [data1][kotlin.Metadata.data1] field with the byte-code of its host class.
 */
internal class PackageMetaFixerTransformer(
    logger: Logger,
    actualFields: Collection<FieldElement>,
    actualMethods: Collection<String>,
    data1: List<String>,
    data2: List<String>
) : MetaFixerTransformer<ProtoBuf.Package>(
    logger,
    actualFields,
    actualMethods,
    emptyList(),
    emptyList(),
    data1,
    data2,
    ProtoBuf.Package::parseFrom
) {
    override val typeTable = TypeTable(message.typeTable)
    override val properties = mutableList(message.propertyList)
    override val functions = mutableList(message.functionList)
    override val constructors = mutableListOf<ProtoBuf.Constructor>()

    override fun rebuild(): ProtoBuf.Package = message.toBuilder().apply {
        clearFunction().addAllFunction(functions)

        if (properties.size != propertyCount) {
            clearProperty().addAllProperty(properties)
        }
    }.build()
}
