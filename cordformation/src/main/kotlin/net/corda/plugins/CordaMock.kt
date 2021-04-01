@file:JvmName("Mocking")
package net.corda.plugins

import java.lang.reflect.Proxy

internal interface CordaMock

internal inline fun <reified T> cordaMock(): T = cordaMock(T::class.java)

internal fun <T> cordaMock(clazz: Class<T>): T {
    return clazz.cast(Proxy.newProxyInstance(
        CordaMock::class.java.classLoader,
        arrayOf(clazz, CordaMock::class.java)
    ) { obj, method, args ->
        when {
            args == null || args.isEmpty() ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(obj)
                    "toString" -> "CordaMock\$${System.identityHashCode(obj)}"
                    else -> method.returnType.nullValue
                }
            args.size == 1 && method.name == "equals" ->
                obj === args[0]
            else ->
                method.returnType.nullValue
        }
    })
}

private val Class<*>.nullValue: Any? get() {
    return if (isPrimitive) {
        when (this) {
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Short::class.javaPrimitiveType -> 0.toShort()
            Byte::class.javaPrimitiveType -> 0.toByte()
            Boolean::class.javaPrimitiveType -> false
            Char::class.javaPrimitiveType -> 0.toChar()
            Double::class.javaPrimitiveType -> 0.0
            Float::class.javaPrimitiveType -> 0.0.toFloat()
            else -> null
        }
    } else {
        null
    }
}
