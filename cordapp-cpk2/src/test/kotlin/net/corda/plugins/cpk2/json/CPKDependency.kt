package net.corda.plugins.cpk2.json

class CPKDependencyFile(
    val formatVersion: String,
    val dependencies: Array<CPKDependency>
)

data class CPKDependency(
    val name: String,
    val version: String,
    val type: String?,
    val verifySameSignerAsMe: Boolean = false,
    val verifyFileHash: VerifyFileHash?,
)