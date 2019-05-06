package net.corda.plugins

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING

@CompileStatic
class Utilities {
    static long installResource(Path folder, String resourceName) {
        Path buildFile = folder.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')))
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