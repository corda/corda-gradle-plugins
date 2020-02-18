package net.corda.gradle.jarfilter.asm

import kotlinx.metadata.KmPackage
import kotlinx.metadata.KmTypeAlias

class FileMetadata(metadata: KmPackage) {
    @JvmField
    val typeAliasNames: List<String> = metadata.typeAliases.map(KmTypeAlias::name)
}