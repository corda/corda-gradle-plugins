@file:JvmName("Elements")
package net.corda.gradle.jarfilter

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag.ValueParameter.DECLARES_DEFAULT_VALUE
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.flagsOf
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import org.objectweb.asm.Opcodes.*
import java.util.*

private const val DEFAULT_CONSTRUCTOR_MARKER = "ILkotlin/jvm/internal/DefaultConstructorMarker;"
private const val DEFAULT_FUNCTION_MARKER = "ILjava/lang/Object;"
private const val DUMMY_METHOD: Int = ACC_PUBLIC or ACC_PROTECTED or ACC_PRIVATE
private const val DUMMY_PASSES = 1

private val DECLARES_DEFAULT_VALUE_MASK: Int = flagsOf(DECLARES_DEFAULT_VALUE).inv()

typealias AnnotatedMethod = Pair<String, MethodElement>
typealias UnwantedMap = MutableMap<ClassName, MutableList<AnnotatedMethod>>

abstract class Element(val name: String, val descriptor: String) {
    private var lifetime: Int = DUMMY_PASSES

    open val isExpired: Boolean get() = --lifetime < 0
}


class MethodElement(name: String, descriptor: String, val access: Int = DUMMY_METHOD) : Element(name, descriptor) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as MethodElement
        return other.name == name && other.descriptor == descriptor
    }
    override fun hashCode(): Int = Objects.hash(name, descriptor)
    override fun toString(): String = "MethodElement[name=$name, descriptor=$descriptor, access=$access]"
    override val isExpired: Boolean get() = isDummy && super.isExpired
    val isConstructor: Boolean get() = isObjectConstructor || isClassConstructor
    val isClassConstructor: Boolean get() = name == "<clinit>"
    val isObjectConstructor: Boolean get() = name == "<init>"
    val isVoidFunction: Boolean get() = !isConstructor && descriptor.endsWith(")V")
    val isDummy: Boolean get() = access == DUMMY_METHOD

    private val suffix: String
    val visibleName: String
    val signature: String = name + descriptor

    init {
        val idx = name.indexOf('$')
        visibleName = if (idx == -1) name else name.substring(0, idx)
        suffix = if (idx == -1) "" else name.drop(idx + 1)
    }

    fun isKotlinSynthetic(vararg tags: String): Boolean = (access and ACC_SYNTHETIC) != 0 && tags.contains(suffix)
    fun asKotlinNonDefaultConstructor(): MethodElement? {
        val markerIdx = descriptor.indexOf(DEFAULT_CONSTRUCTOR_MARKER)
        return if (markerIdx >= 0) {
            MethodElement(name, descriptor.removeRange(markerIdx, markerIdx + DEFAULT_CONSTRUCTOR_MARKER.length))
        } else {
            null
        }
    }

    fun asKotlinDefaultConstructor(): MethodElement? {
        val markerIdx = descriptor.lastIndexOf(')')
        return when {
            descriptor.contains(DEFAULT_CONSTRUCTOR_MARKER) -> this
            markerIdx >= 0 -> MethodElement(
                name = name,
                descriptor = (descriptor.substring(0, markerIdx) + DEFAULT_CONSTRUCTOR_MARKER + descriptor.substring(markerIdx))
            )
            else -> null
        }
    }

    fun asKotlinDefaultFunction(classDescriptor: ClassName): MethodElement? {
        val markerIdx = descriptor.lastIndexOf(')')
        return when {
            suffix == "default" -> this
            descriptor.startsWith('(') && markerIdx >= 1 -> MethodElement(
                name = "$name\$default",
                descriptor = ('(' + classDescriptor + descriptor.substring(1, markerIdx) + DEFAULT_FUNCTION_MARKER + descriptor.substring(markerIdx))
            )
            else -> null
        }
    }
}


/**
 * A class cannot have two fields with the same name but different types. However,
 * it can define extension functions and properties.
 */
class FieldElement(name: String, descriptor: String = "?", val extension: String = "()") : Element(name, descriptor) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as FieldElement
        return other.name == name && other.extension == extension
    }
    override fun hashCode(): Int = Objects.hash(name, extension)
    override fun toString(): String = "FieldElement[name=$name, descriptor=$descriptor, extension=$extension]"
    override val isExpired: Boolean get() = descriptor == "?" && super.isExpired
}

val String.extensionType: String get() = substring(0, 1 + indexOf(')'))

/**
 * Returns a fully-qualified class name as it would exist
 * in the byte-code, e.g. as "a/b/c/ClassName$Nested".
 */
fun ClassName.toInternalName(): ClassName = replace('.', '$')

/**
 * Construct the signatures of the synthetic methods that
 * Kotlin would create to handle default parameter values.
 */
fun String.toKotlinDefaultConstructor(): String {
    val closer = lastIndexOf(')')
    return substring(0, closer) + DEFAULT_CONSTRUCTOR_MARKER + substring(closer)
}

fun String.toKotlinDefaultFunction(classDescriptor: String): String {
    val opener = indexOf('(')
    val closer = lastIndexOf(')')
    return (substring(0, opener) + "\$default("
               + classDescriptor + substring(opener + 1, closer) + DEFAULT_FUNCTION_MARKER
               + substring(closer))
}

/**
 * Convert Kotlin method data to [MethodElement] objects.
 */
fun JvmMethodSignature.toMethodElement() = MethodElement(name, desc)

/**
 * Convert Kotlin field data to [FieldElement] objects.
 */
fun JvmFieldSignature.toFieldElement() = FieldElement(name, desc)

/**
 * Removes the "has a default value" flag from a constructor/function parameter.
 */
fun KmValueParameter.clearDeclaresDefaultValue(): KmValueParameter {
    if (DECLARES_DEFAULT_VALUE(flags)) {
        flags = flags and DECLARES_DEFAULT_VALUE_MASK
    }
    return this
}

val List<KmValueParameter>.hasAnyDefaultValues
    get() = any { DECLARES_DEFAULT_VALUE(it.flags) }
