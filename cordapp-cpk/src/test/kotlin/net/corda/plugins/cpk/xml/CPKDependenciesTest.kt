package net.corda.plugins.cpk.xml

import net.corda.plugins.cpk.xmlContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.util.Base64
import javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT

class CPKDependenciesTest {
    private companion object {
        private const val SYMBOLIC_NAME = "com.example.cordapp1"
        private const val VERSION = "1.0.0.SNAPSHOT"
        private const val HASH_ALGORITHM = "SHA-256"
        private val HASH_DATA = byteArrayOf(0x7f, 0x6e, 0x5d, 0x4c, 0x3b, 0x2a, 0x10, 0x0f)

        private fun writeXML(dependencies: CPKDependencies): String {
            return StringWriter().use { writer ->
                xmlContext.createMarshaller().apply {
                    setProperty(JAXB_FORMATTED_OUTPUT, false)
                }.marshal(dependencies, writer)
                writer.toString()
            }
        }
    }

    @Test
    fun testWriting() {
        val dependencies = CPKDependencies(listOf(
            CPKDependency(
                name = SYMBOLIC_NAME,
                version = VERSION,
                signedBy = HashValue(HASH_DATA, HASH_ALGORITHM))
        ))
        val document = writeXML(dependencies)
        assertEquals("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cpkDependencies xmlns="corda-cpk">
    <cpkDependency>
        <name>$SYMBOLIC_NAME</name>
        <version>$VERSION</version>
        <signedBy algorithm="$HASH_ALGORITHM">${Base64.getEncoder().encodeToString(HASH_DATA)}</signedBy>
    </cpkDependency>
</cpkDependencies>""".replace(">\\s++<".toRegex(), "><"), document)
    }

    @Test
    fun testWritingEmpty() {
        val document = writeXML(CPKDependencies())
        assertEquals(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cpkDependencies xmlns="corda-cpk"/>""",
            document
        )
    }
}