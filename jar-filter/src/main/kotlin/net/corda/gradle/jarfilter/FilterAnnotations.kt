package net.corda.gradle.jarfilter

import org.gradle.api.tasks.Input

open class FilterAnnotations {
    @get:Input
    var forDelete: Set<String> = emptySet()

    @get:Input
    var forStub: Set<String> = emptySet()

    @get:Input
    var forRemove: Set<String> = emptySet()

    @get:Input
    var forSanitise: Set<String> = emptySet()
}