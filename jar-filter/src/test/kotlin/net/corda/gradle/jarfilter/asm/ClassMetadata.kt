package net.corda.gradle.jarfilter.asm

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flags
import kotlinx.metadata.KmClassVisitor
import net.corda.gradle.jarfilter.toPackageFormat

internal class ClassMetadata : KmClassVisitor() {
    private var className: ClassName = ""

    private val _sealedSubclasses: MutableList<ClassName> = mutableListOf()
    val sealedSubclasses: List<ClassName> get() = _sealedSubclasses

    private val _nestedClasses: MutableList<ClassName> = mutableListOf()
    val nestedClasses: List<ClassName> get() = _nestedClasses

    override fun visit(flags: Flags, name: ClassName) {
        className = name.toPackageFormat
    }

    override fun visitNestedClass(name: String) {
        _nestedClasses += "$className\$$name"
    }

    override fun visitSealedSubclass(name: ClassName) {
        _sealedSubclasses += name.replace('.', '$').toPackageFormat
    }
}
