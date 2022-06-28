package net.corda.plugins.cpk2.json

data class CPKDependency(
    val name: String,
    val version: String,
    val type: String?,
    val verifySameSignerAsMe: Boolean = false,
    val verifyFileHash: VerifyFileHash?,
)