package net.corda.plugins

import org.gradle.api.publish.maven.*
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal

open class DelegatedPom(pom: MavenPomInternal): MavenPomInternal by pom {
    override fun getRuntimeDependencyManagement(): MutableSet<MavenDependency> {
        //for now, just return empty set, but in future if we do allow cordapps to have dependencies outside of corda
        //something along the lines of:
        // val depsToExclude: Set<Pair<String, String>> = projectDepCalculator(project)
        // return pom.runtimeDependencyManagement.filterNot { toExcludableDependency(it) in depsToExclude}.toHashSet()
        return hashSetOf()
    }

    override fun getRuntimeDependencies(): MutableSet<MavenDependencyInternal> {
        return hashSetOf()
    }

    override fun getApiDependencyManagement(): MutableSet<MavenDependency> {
        return hashSetOf()
    }

    override fun getApiDependencies(): MutableSet<MavenDependencyInternal> {
        return hashSetOf()
    }
}
