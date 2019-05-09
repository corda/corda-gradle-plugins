package net.corda.example

import net.corda.annotation.AnAnnotation

@AnAnnotation
class LegacyApi {
    init {
        this::class.annotations.forEach {
            println(it.javaClass.name)
        }
    }
}

