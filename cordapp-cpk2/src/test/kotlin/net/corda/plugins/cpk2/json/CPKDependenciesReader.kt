package net.corda.plugins.cpk2.json

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStream

class CPKDependenciesReader{
    companion object {
        fun loadCPKDependencies(input: InputStream): List<CPKDependency> =
            jacksonObjectMapper().readValue(input, Array<CPKDependency>::class.java).toList()
    }
}