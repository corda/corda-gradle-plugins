@file:JvmName("XmlSchema")
package net.corda.plugins.cpk2.xml

import net.corda.plugins.cpk2.createDocumentBuilderFactory
import net.corda.plugins.cpk2.xml.XMLFactory.createSchemaFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.InputStream
import java.lang.invoke.MethodHandles
import java.util.Base64
import java.util.Collections.emptyIterator
import java.util.Collections.unmodifiableList
import javax.xml.parsers.DocumentBuilderFactory

private const val CORDA_CPK_V1 = "/xml/corda-cpk-1.0.xsd"

/**
 * Create expensive [DocumentBuilderFactory] once, for all tests.
 */
private val documentBuilderFactory = createDocumentBuilderFactory().also { dbf ->
    val cpkSchema = createSchemaFactory().newSchema(
        MethodHandles.lookup().lookupClass().getResource(CORDA_CPK_V1) ?: fail("Corda CPK schema missing")
    )
    dbf.schema = cpkSchema
}

private class ElementIterator(private val nodes: NodeList) : Iterator<Element> {
    private var index = 0

    private val next: Element? get() {
        while (index < nodes.length) {
            val element = nodes.item(index) as? Element
            if (element != null) {
                return element
            }
            ++index
        }
        return null
    }

    override fun hasNext(): Boolean {
        return next != null
    }

    override fun next(): Element {
        return (next ?: throw NoSuchElementException()).also {
            ++index
        }
    }
}

val Node.childElements: Iterator<Element> get() {
    return if (hasChildNodes()) {
        ElementIterator(childNodes)
    } else {
        emptyIterator()
    }
}

interface AbstractBuilder<T> {
    fun build(): T
}

class SameAsMe

class HashValue(val value: ByteArray, val algorithm: String) {
    val isSHA256: Boolean
        get() = algorithm == "SHA-256" && value.size == 32

    override fun toString(): String {
        return Base64.getEncoder().encodeToString(value)
    }

    class Builder(private val element: Element) : AbstractBuilder<HashValue> {
        override fun build(): HashValue {
            val algorithm = element.getAttribute("algorithm")
            val value = Base64.getDecoder().decode(element.textContent)
            return HashValue(
                value = value ?: fail("hash.value missing"),
                algorithm = algorithm ?: fail("hash.algorithm missing")
            )
        }
    }
}

class SignersBuilder(private val node: Node) : AbstractBuilder<List<Any>> {
    private val signers = mutableListOf<HashValue>()
    private val sameAsMe = mutableListOf<SameAsMe>()

    override fun build(): List<Any> {
        for (childElement in node.childElements) {
            when (val tagName = childElement.tagName) {
                "signer" -> signers.add(HashValue.Builder(childElement).build())
                "sameAsMe" -> sameAsMe.add(SameAsMe())
                else -> fail("Unknown XML element <$tagName>")
            }
        }
        return if (signers.isEmpty() && sameAsMe.isEmpty()) {
            emptyList()
        } else if (sameAsMe.isEmpty()) {
            unmodifiableList(signers)
        } else if (signers.isEmpty()) {
            unmodifiableList(sameAsMe)
        } else {
            fail("<signers> cannot contain both <signer> and <sameAsMe/> elements.")
        }
    }
}

class CPKDependency(
    val name: String,
    val version: String,
    val type: String?,
    val signers: List<Any>
) {
    override fun toString(): String {
        return """<cpkDependency>
    <name>$name</name>
    <version>$version</version>
    ${formatType()}${formatSigners()}
</cpkDependency>"""
    }

    private fun formatType(): String {
        return type?.let { "<type>$it</type>${System.lineSeparator()}" } ?: ""
    }

    private fun formatSigners(): String {
        return if (signers.isEmpty()) {
            "<signers/>"
        } else {
            signers.joinToString(
                prefix = "<signers>",
                postfix = "</signers>",
                transform = ::formatSigner
            )
        }
    }

    private fun formatSigner(signer: Any): String {
        return when (signer) {
            is HashValue -> """<signer algorithm="${signer.algorithm}">$signer</signer>"""
            is SameAsMe -> "<sameAsMe/>"
            else -> fail("Unknown <signer> element '$signer'")
        }
    }

    class Builder(private val node: Node) : AbstractBuilder<CPKDependency> {
        private var name: String? = null
        private var version: String? = null
        private var type: String? = null
        private var signers: List<Any>? = null

        override fun build(): CPKDependency {
            for (childElement in node.childElements) {
                when (val tagName = childElement.tagName) {
                    "name" -> name = childElement.textContent
                    "version" -> version = childElement.textContent
                    "type" -> type = childElement.textContent
                    "signers" -> signers = SignersBuilder(childElement).build()
                    else -> fail("Unknown XML element <$tagName>")
                }
            }
            return CPKDependency(
                name = name ?: fail("cpkDependency.name missing"),
                version = version ?: fail("cpkDependency.version missing"),
                type = type,
                signers = signers ?: fail("cpkDependency.signers missing")
            )
        }
    }
}

class CPKDependenciesBuilder(private val node: Node) : AbstractBuilder<List<CPKDependency>> {
    private val dependencies = mutableListOf<CPKDependency>()

    override fun build(): List<CPKDependency> {
        for (childElement in node.childElements) {
            when (val tagName = childElement.tagName) {
                "cpkDependency" ->
                    dependencies.add(CPKDependency.Builder(childElement).build())
                else -> fail("Unknown XML element <$tagName>")
            }
        }
        return unmodifiableList(dependencies)
    }
}

private fun loadDocumentFrom(input: InputStream): Document {
    return documentBuilderFactory.newDocumentBuilder().apply {
        setErrorHandler(UnforgivingErrorHandler())
    }.parse(input)
}

private class UnforgivingErrorHandler : ErrorHandler {
    override fun warning(ex: SAXParseException) {
        fail(ex.message)
    }

    override fun error(ex: SAXParseException) {
        fail(ex.message)
    }

    override fun fatalError(ex: SAXParseException) {
        fail(ex.message)
    }
}

fun loadCPKDependencies(input: InputStream): List<CPKDependency> {
    val nodes = loadDocumentFrom(input).getElementsByTagName("cpkDependencies")
    assertEquals(1, nodes.length)
    return CPKDependenciesBuilder(nodes.item(0)).build()
}

