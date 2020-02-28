package net.corda.plugins

import java.awt.GraphicsEnvironment
import java.io.File
import java.util.*

private const val HEADLESS_FLAG = "--headless"
private const val CAPSULE_DEBUG_FLAG = "--capsule-debug"
private const val BASE_DEBUG_PORT_FLAG = "--base-debug-port="
private const val BASE_MONITORING_PORT_FLAG = "--base-monitoring-port="
private const val CORDA_JAR_NAME = "corda.jar"
private const val CORDA_WEBSERVER_JAR_NAME = "corda-testserver.jar"
private const val OLD_CORDA_WEBSERVER_JAR_NAME = "corda-webserver.jar"
private const val CORDA_CONFIG_NAME = "node.conf"
private const val CORDA_WEBSERVER_CONFIG_NAME = "web-server.conf"
private val CORDA_HEADLESS_ARGS = listOf("--no-local-shell")

private const val DEFAULT_BASE_DEBUG_PORT = 5005
private const val DEFAULT_BASE_MONITORING_PORT = 7005

private val os by lazy {
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
    if ("mac" in osName || "darwin" in osName) OS.MACOS
    else if ("win" in osName) OS.WINDOWS
    else OS.LINUX
}

private enum class OS { MACOS, WINDOWS, LINUX }

private class PortAlloc(private var basePort: Int) {
    internal fun next() = basePort++
}

fun main(args: Array<String>) {
    val isHeadless = ((!isTmux() && GraphicsEnvironment.isHeadless()) || args.contains(HEADLESS_FLAG))
    val capsuleDebugMode = args.contains(CAPSULE_DEBUG_FLAG)
    val baseDebugPort = parseArg(args, BASE_DEBUG_PORT_FLAG, DEFAULT_BASE_DEBUG_PORT)
    println("base debug port set to： $baseDebugPort")
    val baseMonitoringPort = parseArg(args, BASE_MONITORING_PORT_FLAG, DEFAULT_BASE_MONITORING_PORT)
    println("base monitoring port set to: $baseMonitoringPort")
    val javaArgs = args.filter { it != HEADLESS_FLAG && it != CAPSULE_DEBUG_FLAG && !it.startsWith(BASE_DEBUG_PORT_FLAG) && !it.startsWith(BASE_MONITORING_PORT_FLAG) }
    val jvmArgs = if (capsuleDebugMode) listOf("-Dcapsule.log=verbose") else emptyList()
    NodeRunner(baseDebugPort, baseMonitoringPort).run(isHeadless, javaArgs, jvmArgs)
    println("Finished starting nodes")
}

private class NodeRunner(baseDebugPort: Int, baseMonitoringPort: Int) {
    private val debugPortAlloc = PortAlloc(baseDebugPort)
    private val monitoringPortAlloc = PortAlloc(baseMonitoringPort)

    fun run(isHeadless: Boolean, javaArgs: List<String>, jvmArgs: List<String>) {
        val startedProcesses = mutableListOf<Process>()
        val workingDir = File(System.getProperty("user.dir"))
        println("Starting nodes in $workingDir")
        workingDir.listFiles { file -> file.isDirectory }.forEach { dir ->
            startNode(dir, isHeadless, jvmArgs, javaArgs)?.let { startedProcesses += it }
            startWebserver(dir, isHeadless, jvmArgs, javaArgs)?.let { startedProcesses += it }
        }
        println("Started ${startedProcesses.size} processes")
    }

    private fun startNode(nodeDir: File, headless: Boolean, jvmArgs: List<String>, javaArgs: List<String>): Process? {
        val jarFile = nodeDir.resolve(CORDA_JAR_NAME)
        return if (!jarFile.isFile) {
            println("No file $CORDA_JAR_NAME found in $nodeDir")
            null
        } else if (!nodeDir.resolve(CORDA_CONFIG_NAME).isFile) {
            println("Node conf file $CORDA_CONFIG_NAME not found in $nodeDir")
            null
        } else {
            val debugPort = debugPortAlloc.next()
            println("Starting $CORDA_JAR_NAME in $nodeDir on debug port $debugPort")
            startJar(jarFile, headless, jvmArgs + getDebugArgs(debugPort) + getJolokiaArgs(nodeDir), javaArgs, CORDA_HEADLESS_ARGS)
        }
    }

    private fun startWebserver(nodeDir: File, headless: Boolean, jvmArgs: List<String>, javaArgs: List<String>): Process? {
        val jarFile = nodeDir.resolve(CORDA_WEBSERVER_JAR_NAME).let { if (it.exists()) it else nodeDir.resolve(OLD_CORDA_WEBSERVER_JAR_NAME) }
        return if (!jarFile.isFile) {
            null
        } else if (!nodeDir.resolve(CORDA_WEBSERVER_CONFIG_NAME).isFile) {
            println("Webserver conf file $CORDA_WEBSERVER_CONFIG_NAME not found in $nodeDir")
            null
        } else {
            println("Starting $CORDA_WEBSERVER_JAR_NAME in $nodeDir")
            startJar(jarFile, headless, jvmArgs, javaArgs)
        }
    }

    private fun startJar(jar: File, headless: Boolean, jvmArgs: List<String>, javaArgs: List<String>, headlessArgs: List<String> = emptyList()): Process {
        val workingDir = jar.parentFile
        val nodeName = workingDir.name
        val command = getBaseCommand(jvmArgs, nodeName) + listOf("-jar", jar.absolutePath) + javaArgs
        val process = if (headless) startHeadless(command, workingDir, nodeName, headlessArgs) else startWindowed(command, workingDir, nodeName)
        if (os == OS.MACOS && !isScreen()) Thread.sleep(1000)
        return process
    }

    private fun startHeadless(command: List<String>, workingDir: File, nodeName: String, headlessArgs: List<String> = emptyList()): Process {
        println("Running command: ${command.joinToString(" ")}")
        return ProcessBuilder(command + headlessArgs).redirectError(File("error.$nodeName.log")).inheritIO().directory(workingDir).start()
    }

    private fun startWindowed(command: List<String>, workingDir: File, nodeName: String): Process {
        val params = when (os) {
            OS.MACOS -> {
                if (isScreen()) {
                    listOf("screen", "-X", "screen", "-t", nodeName, "sh", "-c", listOf("cd", workingDir.absolutePath, "&&").plus(command).joinToString(" "))
                } else {
                    listOf("osascript", "-e", """tell app "Terminal"
    activate
    delay 0.5
    tell app "System Events" to tell process "Terminal" to keystroke "t" using command down
    delay 0.5
    do script "bash -c 'cd \"$workingDir\" ; \"${command.joinToString("""\" \"""")}\" && exit'" in selected tab of the front window
end tell""")
                }
            }
            OS.WINDOWS -> {
                listOf("cmd", "/C", "start ${command.joinToString(" ") { windowsSpaceEscape(it) }}")
            }
            OS.LINUX -> {
                // Start shell to keep window open unless java terminated normally or due to SIGTERM:
                val unixCommand = "${unixCommand(command)}; [ $? -eq 0 -o $? -eq 143 ] || sh"
                if (isTmux()) {
                    listOf("tmux", "new-window", "-n", nodeName, unixCommand)
                } else {
                    listOf("xterm", "-T", nodeName, "-e", unixCommand)
                }
            }
        }
        println("Running command: ${params.joinToString(" ")}")
        return ProcessBuilder(params).directory(workingDir).start()
    }

    private fun getBaseCommand(baseJvmArgs: List<String>, nodeName: String): List<String> {
        val jvmArgs = if (baseJvmArgs.isNotEmpty()) {
            listOf("-Dcapsule.jvm.args=${baseJvmArgs.joinToString(separator = " ")}")
        } else {
            emptyList()
        }
        return listOf(getJavaPath()) + jvmArgs + listOf("-Dname=$nodeName")
    }

    private fun getDebugArgs(debugPort: Int) = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort")
    private fun getJolokiaArgs(dir: File): List<String> {
        val jolokiaJar = File("$dir/drivers").listFiles { _, filename ->
            filename.matches("jolokia-jvm-.*-agent\\.jar$".toRegex())
        }.firstOrNull()?.name

        return if (jolokiaJar != null) {
            val monitoringPort = monitoringPortAlloc.next()
            println("Node will expose jolokia monitoring port on $monitoringPort")

            listOf("-javaagent:drivers/$jolokiaJar=port=$monitoringPort,logHandlerClass=net.corda.node.JolokiaSlf4jAdapter")
        } else {
            emptyList()
        }
    }
}

private fun parseArg(args: Array<String>, flagPrefix: String, defaultValue: Int): Int {
    return try {
        args.firstOrNull { it.startsWith(flagPrefix) }?.removePrefix(flagPrefix)?.run(Integer::valueOf) ?: defaultValue
    } catch (_: NumberFormatException) {
        println("Invalid option - using default: $flagPrefix$defaultValue")
        defaultValue
    }
}

private fun quotedFormOf(text: String) = "'${text.replace("'", "'\\''")}'" // Suitable for UNIX shells.
private fun isTmux() = System.getenv("TMUX")?.isNotEmpty() ?: false
private fun isScreen() = System.getenv("TERM") == "screen"
// Replace below is to fix an issue with spaces in paths on Windows.
// Quoting the entire path does not work, only the space or directory within the path.
private fun windowsSpaceEscape(s:String) = s.replace(" ", "\" \"")
private fun unixCommand(command: List<String>) = command.map(::quotedFormOf).joinToString(" ")
private fun getJavaPath(): String = File(File(System.getProperty("java.home"), "bin"), "java").path