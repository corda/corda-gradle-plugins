package net.corda.plugins.cpb

import net.corda.plugins.cpk.CORDAPP_TASK_GROUP
import net.corda.plugins.cpk.CORDA_CPK_TYPE
import net.corda.plugins.cpk.CPK_FILE_EXTENSION
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import java.nio.file.Files
import java.util.jar.JarInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

open class CpbTask @Inject constructor(objects: ObjectFactory) : Jar() {

    companion object {
        private const val CPB_ARTIFACT_CLASSIFIER = "package"
        const val CPB_FILE_EXTENSION = "cpb"
        private const val CPK_FILE_SUFFIX = ".$CPK_FILE_EXTENSION"
        private const val JAR_FILE_SUFFIX = ".jar"
        private val EXCLUDED_CPK_TYPES = setOf("corda-api")
        const val CPB_NAME_ATTRIBUTE = "Corda-CPB-Name"
        const val CPB_VERSION_ATTRIBUTE = "Corda-CPB-Version"
    }


    init {
        group = CORDAPP_TASK_GROUP
        description = "Assembles a .cpb archive that contains the current project's .cpk artifact " +
                "and all of its dependencies"
        archiveClassifier.set(CPB_ARTIFACT_CLASSIFIER)
        archiveExtension.set(CPB_FILE_EXTENSION)
        dirMode = Integer.parseInt("555", 8)
        duplicatesStrategy = DuplicatesStrategy.FAIL
        fileMode = Integer.parseInt("444", 8)
        entryCompression = ZipEntryCompression.STORED
        manifestContentCharset = "UTF-8"
        metadataCharset = "UTF-8"
        includeEmptyDirs = false
        isCaseSensitive = true
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        isZip64 = true

        manifest { m ->
            m.attributes[CPB_NAME_ATTRIBUTE] = archiveBaseName
            m.attributes[CPB_VERSION_ATTRIBUTE] = archiveVersion
        }
    }

    override fun from(vararg args : Any) : AbstractCopyTask {
        return from(args) { copySpec ->
            copySpec.exclude { fileTreeElement ->
                fileTreeElement.takeIf { it.name.endsWith(CPK_FILE_SUFFIX) }
                    ?.file
                    ?.toPath()
                    ?.let {
                        ZipInputStream(Files.newInputStream(it)).use { cpkStream ->
                            generateSequence(cpkStream.nextEntry) { cpkStream.nextEntry }
                                .find { zipEntry ->
                                    // Check this entry represent the CPK's main jar
                                    val slash = zipEntry.name.lastIndexOf('/')
                                    zipEntry.name.endsWith(JAR_FILE_SUFFIX) && (slash == -1)
                                }?.let {
                                    JarInputStream(cpkStream).use { mainJarInputStream ->
                                        mainJarInputStream.manifest.mainAttributes.getValue(CORDA_CPK_TYPE)
                                            ?.let { cpkType ->
                                                cpkType.toLowerCase() in EXCLUDED_CPK_TYPES
                                            }
                                    }
                                }
                        }
                    } ?: false
            }
        }
    }
}
