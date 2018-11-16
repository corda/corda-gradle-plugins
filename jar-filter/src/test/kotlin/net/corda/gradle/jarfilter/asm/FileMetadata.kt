package net.corda.gradle.jarfilter.asm

import kotlinx.metadata.internal.metadata.ProtoBuf
import kotlinx.metadata.internal.metadata.deserialization.TypeTable
import net.corda.gradle.jarfilter.MetadataTransformer
import net.corda.gradle.jarfilter.mutableList
import org.gradle.api.logging.Logger

internal class FileMetadata(
    logger: Logger,
    d1: List<String>,
    d2: List<String>
) : MetadataTransformer<ProtoBuf.Package>(
    logger,
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    {},
    d1,
    d2,
    ProtoBuf.Package::parseFrom
) {
    override val typeTable = TypeTable(message.typeTable)
    override val properties = mutableList(message.propertyList)
    override val functions = mutableList(message.functionList)
    override val typeAliases = mutableList(message.typeAliasList)

    override fun rebuild(): ProtoBuf.Package = message

    val typeAliasNames: List<String> = typeAliases.map { nameResolver.getString(it.name) }.toList()
}