@file:JvmName("AttributeUtils")
package net.corda.plugins.cpk

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE
import org.gradle.api.attributes.Bundling.EXTERNAL
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.ENFORCED_PLATFORM
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.Category.REGULAR_PLATFORM
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Usage.JAVA_API
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.model.ObjectFactory

/**
 * Identify a platform dependency by examining its
 * [Category][org.gradle.api.attributes.Category]
 * variant attribute.
 */
fun isPlatformModule(dependency: ModuleDependency): Boolean {
    val attributeName = (dependency.attributes.getAttribute(CATEGORY_ATTRIBUTE) ?: return false).name
    return attributeName == REGULAR_PLATFORM || attributeName == ENFORCED_PLATFORM
}

internal class AttributeFactory(
    private val attrs: AttributeContainer,
    private val objects: ObjectFactory) {

    fun javaRuntime(): AttributeFactory {
        attrs.attribute(USAGE_ATTRIBUTE, objects.named(Usage::class.java, JAVA_RUNTIME))
        return this
    }

    fun javaApi(): AttributeFactory {
        attrs.attribute(USAGE_ATTRIBUTE, objects.named(Usage::class.java, JAVA_API))
        return this
    }

    fun asLibrary(): AttributeFactory {
        attrs.attribute(CATEGORY_ATTRIBUTE, objects.named(Category::class.java, LIBRARY))
        return this
    }

    fun jar(): AttributeFactory {
        attrs.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, JAR))
        return this
    }

    fun cpk(): AttributeFactory {
        attrs.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, "cpk"))
        return this
    }

    fun cpb(): AttributeFactory {
        attrs.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, "cpb"))
        return this
    }

    fun withExternalDependencies(): AttributeFactory {
        attrs.attribute(BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, EXTERNAL))
        return this
    }
}
