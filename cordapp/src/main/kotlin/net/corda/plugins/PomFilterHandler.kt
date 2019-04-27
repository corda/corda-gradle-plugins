@file:JvmName("PomFilter")
package net.corda.plugins

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.publish.maven.*
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Implements a dynamic proxy for the [MavenPomInternal] interface.
 * This protects us from breaking, should the interface acquire
 * extra methods in later versions of Gradle.
 */
private class PomFilterHandler(private val pom: MavenPomInternal) : InvocationHandler {
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        //for now, just return empty set for getRuntimeDependencyManagement(), but in future
        //if we do allow cordapps to have dependencies outside of corda something along the
        //lines of:
        // val depsToExclude: Set<Pair<String, String>> = projectDepCalculator(project)
        // pom.runtimeDependencyManagement.filterNot { toExcludableDependency(it) in depsToExclude}.toHashSet()
        return when {
            isDependencyManagementGetter(method)-> hashSetOf<MavenDependency>()
            isDependenciesGetter(method) -> hashSetOf<MavenDependencyInternal>()
            else -> try {
                method.invoke(pom, *(args ?: emptyArray()))
            } catch (e : IllegalAccessException) {
                throw InvalidUserCodeException(e.message ?: "", e)
            } catch (e : IllegalArgumentException) {
                throw InvalidUserCodeException(e.message ?: "", e)
            } catch (e : InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    private fun isDependencyManagementGetter(method: Method): Boolean = with(method) {
        (name == "getRuntimeDependencyManagement" || name == "getApiDependencyManagement")
            && returnType.isAssignableFrom(HashSet::class.java)
    }

    private fun isDependenciesGetter(method: Method): Boolean = with(method) {
        (name == "getRuntimeDependencies" || name == "getApiDependencies")
            && returnType.isAssignableFrom(HashSet::class.java)
    }
}

/**
 * @param pom
 * @return If [pom] is an instance of [MavenPomInternal] then
 * return a dynamic proxy that will contain no API or runtime
 * dependencies. Otherwise return [pom] unchanged.
 */
fun filterDependenciesFor(pom: MavenPom): MavenPom {
    return if (pom is MavenPomInternal) {
        Proxy.newProxyInstance(
            MavenPomInternal::class.java.classLoader,
            arrayOf(MavenPomInternal::class.java),
            PomFilterHandler(pom)
        ) as MavenPomInternal
    } else {
        pom
    }
}
