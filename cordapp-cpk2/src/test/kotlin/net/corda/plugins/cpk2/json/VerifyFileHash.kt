package net.corda.plugins.cpk2.json

data class VerifyFileHash(
    val algorithm: String,
    val fileHash: String)
