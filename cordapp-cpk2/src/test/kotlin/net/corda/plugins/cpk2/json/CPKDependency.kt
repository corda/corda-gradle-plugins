package net.corda.plugins.cpk2.json

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.InputStream

private val mapper = jacksonObjectMapper()

fun loadCPKDependencies(input: InputStream): List<CPKDependency> =
    mapper.readValue<CPKDependencyFile>(input).dependencies

class CPKDependencyFile(
    val formatVersion: String,
    val dependencies: List<CPKDependency>
)

data class CPKDependency(
    val name: String,
    val version: String,
    val type: String?,
    val verifySameSignerAsMe: Boolean = false,
    val verifyFileHash: VerifyFileHash?,
)