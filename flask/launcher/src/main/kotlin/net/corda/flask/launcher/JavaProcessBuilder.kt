package net.corda.flask.launcher

import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class JavaProcessBuilder2(private val mainClass: String) {

    companion object {
        const val PROCESS_BUILDER_PREFIX = "javaProcessBuilder"
    }

    var javaHome = System.getProperty("java.home")
    val jvmArgs = mutableListOf<String>()
    val classpath = mutableListOf<String>()
    val properties = Properties()
    val cliArgs = mutableListOf<String>()

    fun exec(): Int {
        val javaBin = Paths.get(javaHome, "bin", "java")
        val propertyStream = properties.entries.asSequence()
            .map { entry: Map.Entry<Any, Any> -> "-D${entry.key}=${entry.value}" }
        var argLength = 0

        val cmd = let {
            val classPathSequence = if(classpath.isNotEmpty())
                sequenceOf("-cp", classpath.joinToString(System.getProperty("path.separator")))
            else sequenceOf()
            (sequenceOf(javaBin.toString()) + jvmArgs.asSequence() + classPathSequence
                    + propertyStream + sequenceOf(mainClass) + cliArgs.asSequence())
        }.onEach {
            argLength += it.length
        }.toList()
        //Add space between arguments
        argLength += cmd.size -1
        return if(argLength < 1024 || cmd.size == 1) {
            val process = ProcessBuilder(cmd).inheritIO().start()
            process.waitFor()
        } else {
            val argumentFile = Files.createTempFile(PROCESS_BUILDER_PREFIX, null)
            Files.newBufferedWriter(argumentFile).use { writer ->
                writer.write(cmd[1])
                for(i in 2 until cmd.size) {
                    writer.write(" ")
                    writer.write(cmd[i])
                }
            }
            val process = ProcessBuilder(listOf(cmd.first(), "@$argumentFile")).inheritIO().start()
            try {
                process.waitFor()
            } finally {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                Files.delete(argumentFile)
            }
        }
    }
}