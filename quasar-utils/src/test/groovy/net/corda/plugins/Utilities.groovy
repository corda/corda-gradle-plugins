package net.corda.plugins

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING

@CompileStatic
class Utilities {
    static long installResource(Path rootDir, String resourceName) {
        Path buildFile = Paths.get(rootDir.toAbsolutePath().toString(), resourceName.split('/'))
        Files.createDirectories(buildFile.parent)
        return copyResourceTo(resourceName, buildFile)
    }

    static long copyResourceTo(String resourceName, Path target) {
        InputStream input = Utilities.class.classLoader.getResourceAsStream(resourceName)
        if (input == null) {
            throw new FileNotFoundException(resourceName)
        }
        try {
            return Files.copy(input, target, REPLACE_EXISTING)
        } finally {
            input.close()
        }
    }
}