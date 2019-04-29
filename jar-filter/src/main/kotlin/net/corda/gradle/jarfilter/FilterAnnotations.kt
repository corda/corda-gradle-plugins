package net.corda.gradle.jarfilter

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import javax.inject.Inject

open class FilterAnnotations @Inject constructor(objects: ObjectFactory) {
    @get:Input
    val forDelete: SetProperty<String> = objects.setProperty(String::class.java)

    @get:Input
    val forStub: SetProperty<String> = objects.setProperty(String::class.java)

    @get:Input
    val forRemove: SetProperty<String> = objects.setProperty(String::class.java)

    @get:Input
    val forSanitise: SetProperty<String> = objects.setProperty(String::class.java)

    @get:Internal
    val values get() = Values(
        forDelete.get(),
        forStub.get(),
        forRemove.get(),
        forSanitise.get()
    )

    class Values(
        val forDelete: Set<String>,
        val forStub: Set<String>,
        val forRemove: Set<String>,
        val forSanitise: Set<String>
    )
}