package net.corda.gradle.jarfilter.asm

import kotlinx.metadata.ClassName
import kotlinx.metadata.KmClass
import net.corda.gradle.jarfilter.toInternalName
import net.corda.gradle.jarfilter.toPackageFormat

class ClassMetadata(metadata: KmClass) {
    private val className: ClassName = metadata.name.toPackageFormat

    val sealedSubclasses: List<ClassName> = metadata.sealedSubclasses.map {
        it.toInternalName().toPackageFormat
    }

    val nestedClasses: List<ClassName> = metadata.nestedClasses.map { name ->
        "$className\$$name"
    }
}
