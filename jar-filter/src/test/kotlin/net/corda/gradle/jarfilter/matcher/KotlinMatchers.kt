@file:JvmName("KotlinMatchers")
package net.corda.gradle.jarfilter.matcher

import org.hamcrest.Description
import org.hamcrest.DiagnosingMatcher
import org.hamcrest.Matcher
import org.hamcrest.core.IsEqual.equalTo
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty2
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection.Companion.invariant
import kotlin.reflect.full.createType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmName

fun isFunction(name: Matcher<in String>, returnType: Matcher<in String>, vararg parameters: Matcher<in KParameter>): Matcher<in KFunction<*>> {
    return KFunctionMatcher(name, returnType, *parameters)
}

fun isFunction(name: String, returnType: KClass<*>, vararg parameters: KClass<*>): Matcher<in KFunction<*>> {
    return isFunction(equalTo(name), matches(returnType), *parameters.toMatchers())
}

fun isKonstructor(returnType: Matcher<in String>, vararg parameters: Matcher<in KParameter>): Matcher<in KFunction<*>> {
    return KFunctionMatcher(equalTo("<init>"), returnType, *parameters)
}

fun isKonstructor(returnType: KClass<*>, vararg parameters: KClass<*>): Matcher<in KFunction<*>> {
    return isKonstructor(matches(returnType), *parameters.toMatchers())
}

fun isKonstructor(returnType: String, vararg parameters: KClass<*>): Matcher<in KFunction<*>> {
    return isKonstructor(equalTo(returnType), *parameters.toMatchers())
}

fun hasParam(type: Matcher<in String>): Matcher<KParameter> = KParameterMatcher(type)

fun hasParam(type: KClass<*>): Matcher<KParameter> = hasParam(matches(type))

fun isProperty(name: String, type: KClass<*>): Matcher<in KProperty<*>> = isProperty(equalTo(name), matches(type))

fun isProperty(name: Matcher<in String>, type: Matcher<in String>): Matcher<in KProperty<*>> = KPropertyMatcher(name, type)

fun isExtensionProperty(name: String, type: KClass<*>, extensionType: KType): Matcher<in KProperty<*>>
    = isExtensionProperty(equalTo(name), matches(type), matches(extensionType))

fun isExtensionProperty(name: Matcher<in String>, type: Matcher<in String>, extensionType: Matcher<in String>): Matcher<in KProperty<*>>
    = KExtensionPropertyMatcher(name, type, extensionType)

fun isKClass(name: String): Matcher<in KClass<*>> = KClassMatcher(equalTo(name))

fun matches(type: KClass<*>): Matcher<in String> = equalTo(type.qualifiedName)

fun matches(type: KType): Matcher<in String> = equalTo(type.toString())

fun typeOfMap(keyType: KClass<*>, valueType: KClass<*>) = Map::class.createType(listOf(
    invariant(keyType.createType()), invariant(valueType.createType())
))

fun typeOfCollection(elementType: KClass<*>) = Collection::class.createType(
    listOf(invariant(elementType.createType()))
)

fun typeOfList(elementType: KClass<*>) = List::class.createType(
    listOf(invariant(elementType.createType()))
)

private fun Array<out KClass<*>>.toMatchers() = map(::hasParam).toTypedArray()

/**
 * Matcher logic for a Kotlin [KFunction] object. Also applicable to constructors.
 */
private class KFunctionMatcher(
    private val name: Matcher<in String>,
    private val returnType: Matcher<in String>,
    vararg parameters: Matcher<in KParameter>
) : DiagnosingMatcher<KFunction<*>>() {
    private val parameters = listOf(*parameters)

    override fun describeTo(description: Description) {
        description.appendText("KFunction[name as ").appendDescriptionOf(name)
            .appendText(", returnType as ").appendDescriptionOf(returnType)
            .appendText(", parameters as (")
        if (parameters.isNotEmpty()) {
            val param = parameters.iterator()
            description.appendDescriptionOf(param.next())
            while (param.hasNext()) {
                description.appendText(",").appendDescriptionOf(param.next())
            }
        }
        description.appendText(")]")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val function = obj as? KFunction<*> ?: return false
        mismatch.appendText("name is ").appendValue(function.name)
        if (!name.matches(function.name)) {
            return false
        }
        function.returnType.toString().apply {
            if (!returnType.matches(this)) {
                mismatch.appendText(" with returnType ").appendValue(this)
                return false
            }
        }

        val parameterCount = function.valueParameters.size
        if (parameterCount != parameters.size) {
            mismatch.appendText(" with ")
                .appendValue(parameterCount).appendText(" parameter").appendText(if (parameterCount == 1) " " else "s ")
                .appendValueList("(", ",", ")", function.valueParameters.map(KParameter::type))
            return false
        }

        for ((i, param) in function.valueParameters.withIndex()) {
            if (!parameters[i].matches(param)) {
                mismatch.appendText(" where parameter").appendValue(i).appendText(" has type ").appendValue(param.type)
                return false
            }
        }
        return true
    }
}

/**
 * Matcher logic for a Kotlin [KParameter] object.
 */
private class KParameterMatcher(
   private val type: Matcher<in String>
) : DiagnosingMatcher<KParameter>() {
    override fun describeTo(description: Description) {
        description.appendText("KParameter[type as ").appendDescriptionOf(type)
            .appendText("]")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val parameter = obj as? KParameter ?: return false
        parameter.type.toString().apply {
            if (!type.matches(this)) {
                mismatch.appendText("type is ").appendValue(this)
                return false
            }
        }
        return true
    }
}

/**
 * Matcher logic for a Kotlin [KProperty] object.
 */
private class KPropertyMatcher(
    private val name: Matcher<in String>,
    private val type: Matcher<in String>
) : DiagnosingMatcher<KProperty<*>>() {
    override fun describeTo(description: Description) {
        description.appendText("KProperty[name as ").appendDescriptionOf(name)
            .appendText(", type as ").appendDescriptionOf(type)
            .appendText("]")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val property = obj as? KProperty1<*,*> ?: return false
        mismatch.appendText("name is ").appendValue(property.name)
        if (!name.matches(property.name)) {
            return false
        }
        property.returnType.toString().apply {
            if (!type.matches(this)) {
                mismatch.appendText(" and type is ").appendValue(this)
                return false
            }
        }
        return true
    }
}

/**
 * Matcher logic for a Kotlin extension property.
 */
private class KExtensionPropertyMatcher(
    private val name: Matcher<in String>,
    private val type: Matcher<in String>,
    private val extensionType: Matcher<in String>
) : DiagnosingMatcher<KProperty<*>>() {
    override fun describeTo(description: Description) {
        description.appendText("KProperty[name as ").appendDescriptionOf(name)
            .appendText(", type as ").appendDescriptionOf(type)
            .appendText(", extensionType as ").appendDescriptionOf(extensionType)
            .appendText("]")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val property = obj as? KProperty2<*,*,*> ?: return false
        mismatch.appendText("name is ").appendValue(property.name)
        if (!name.matches(property.name)) {
            return false
        }
        property.returnType.toString().apply {
            if (!type.matches(this)) {
                mismatch.appendText(" and type is ").appendValue(this)
                return false
            }
        }
        property.parameters[1].type.toString().apply {
            if (!extensionType.matches(this)) {
                mismatch.appendText(" and extensionType is ").appendValue(this)
                return false
            }
        }
        return true
    }
}

/**
 * Matcher logic for a Kotlin [KClass] object.
 */
private class KClassMatcher(private val className: Matcher<in String>) : DiagnosingMatcher<KClass<*>>() {
    override fun describeTo(description: Description) {
        description.appendText("KClass[name as ").appendDescriptionOf(className)
            .appendText("]")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val type = obj as? KClass<*> ?: return false
        type.jvmName.apply {
            if (!className.matches(this)) {
                mismatch.appendText("name is ").appendValue(this)
                return false
            }
        }
        return true
    }
}