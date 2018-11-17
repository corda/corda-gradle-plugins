package net.corda.gradle.jarfilter.asm

import kotlinx.metadata.Flags
import kotlinx.metadata.KmPackageVisitor
import kotlinx.metadata.KmTypeAliasVisitor

internal class FileMetadata : KmPackageVisitor() {
    private val _typeAliasNames: MutableList<String> = mutableListOf()
    val typeAliasNames: List<String> get() = _typeAliasNames

    override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? {
        _typeAliasNames += name
        return super.visitTypeAlias(flags, name)
    }
}