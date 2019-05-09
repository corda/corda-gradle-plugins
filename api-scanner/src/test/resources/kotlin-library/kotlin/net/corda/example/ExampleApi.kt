package net.corda.example

import net.corda.annotation.AnAnnotation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@AnAnnotation
class ExampleApi {
    private val log: Logger = LoggerFactory.getLogger(ExampleApi::class.java)

    init {
        this::class.annotations.forEach {
            log.info(it.javaClass.name)
        }
    }
}

