package net.corda.plugins

import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING

class Utilities {
    static long installResource(TemporaryFolder folder, String resourceName) {
        File buildFile = folder.newFile(resourceName.substring(1 + resourceName.lastIndexOf('/')))
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

    static long copyResourceTo(String resourceName, File  target) {
        return copyResourceTo(resourceName, target.toPath())
    }
}