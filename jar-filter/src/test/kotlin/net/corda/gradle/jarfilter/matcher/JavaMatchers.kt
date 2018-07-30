@file:JvmName("JavaMatchers")
package net.corda.gradle.jarfilter.matcher

import org.hamcrest.Description
import org.hamcrest.DiagnosingMatcher
import org.hamcrest.Matcher
import org.hamcrest.core.IsEqual.*
import java.lang.reflect.Method
import kotlin.reflect.KClass

fun isMethod(name: Matcher<in String>, returnType: Matcher<in String>, vararg parameters: Matcher<in String>): Matcher<Method> {
    return MethodMatcher(name, returnType, *parameters)
}

fun isMethod(name: String, returnType: Class<*>, vararg parameters: Class<*>): Matcher<Method> {
    return isMethod(equalTo(name), matches(returnType), *parameters.toMatchers())
}

fun matches(type: Class<*>): Matcher<in String> = equalTo(type.name)

private fun Array<out Class<*>>.toMatchers() = map(::matches).toTypedArray()

val KClass<*>.javaDeclaredMethods: List<Method> get() = java.declaredMethods.toList()

/**
 * Matcher logic for a Java [Method] object. Also applicable to constructors.
 */
private class MethodMatcher(
    private val name: Matcher<in String>,
    private val returnType: Matcher<in String>,
    vararg parameters: Matcher<in String>
) : DiagnosingMatcher<Method>() {
    private val parameters = listOf(*parameters)

    override fun describeTo(description: Description) {
        description.appendText("Method[name as ").appendDescriptionOf(name)
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

        val method: Method = obj as? Method ?: return false
        mismatch.appendText("name is ").appendValue(method.name)
        if (!name.matches(method.name)) {
            return false
        }
        method.returnType.apply {
            if (!returnType.matches(name)) {
                mismatch.appendText(" with returnType ").appendValue(name)
                return false
            }
        }

        val parameterCount = method.parameterTypes.size
        if (parameterCount != parameters.size) {
            mismatch.appendText(" with ")
                    .appendValue(parameterCount).appendText(" parameter").appendText(if (parameterCount == 1) " " else "s ")
                    .appendValueList("(", ",", ")", method.parameterTypes.map(Class<*>::getName))
            return false
        }

        for ((i, paramType) in method.parameterTypes.withIndex()) {
            if (!parameters[i].matches(paramType.name)) {
                mismatch.appendText(" where parameter").appendValue(i).appendText(" has type ").appendValue(paramType.name)
                return false
            }
        }
        return true
    }
}
