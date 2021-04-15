@file:JvmName("XmlSchema")
package net.corda.plugins.cpk.xml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.InputStream
import java.util.Base64
import java.util.Collections.unmodifiableList
import javax.xml.parsers.DocumentBuilderFactory

interface AbstractBuilder<T> {
    fun build(): T

    @JvmDefault
    fun getTextValue(node: Node): String? {
        val valueNode = node.firstChild
        return if (valueNode.nodeType == Node.TEXT_NODE) {
            valueNode.nodeValue
        } else {
            null
        }
    }
}

class HashValue(val value: ByteArray, val algorithm: String) {
    val isSHA256: Boolean
        get() = algorithm == "SHA-256" && value.size == 32

    override fun toString(): String {
        return Base64.getEncoder().encodeToString(value)
    }

    class Builder(private val node: Node) : AbstractBuilder<HashValue> {
        private var value: ByteArray? = null
        private var algorithm: String? = null

        override fun build(): HashValue {
            if (node.hasAttributes()) {
                val algorithmNode = node.attributes.getNamedItem("algorithm")
                algorithm = algorithmNode.nodeValue
            }
            value = Base64.getDecoder().decode(getTextValue(node))
            return HashValue(
                value = value ?: fail("hash.value missing"),
                algorithm = algorithm ?: fail("hash.algorithm missing")
            )
        }
    }
}

class SignersBuilder(private val node: Node) : AbstractBuilder<List<HashValue>> {
    private val signers = mutableListOf<HashValue>()

    override fun build(): List<HashValue> {
        if (node.hasChildNodes()) {
            val childNodes = node.childNodes
            for (idx in 0 until childNodes.length) {
                val childNode = childNodes.item(idx)
                when (childNode.nodeName) {
                    "signer" -> signers.add(HashValue.Builder(childNode).build())
                }
            }
        }
        return unmodifiableList(signers)
    }
}

class CPKDependency(
    val name: String,
    val version: String,
    val signers: List<HashValue>
) {
    override fun toString(): String {
        return """<cpkDependency>
    <name>$name</name>
    <version>$version</version>
    ${formatSigners()}
</cpkDependency>"""
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

    private fun formatSigner(signer: HashValue): String {
        return """<signer algorithm="${signer.algorithm}">$signer</signer>"""
    }

    class Builder(private val node: Node) : AbstractBuilder<CPKDependency> {
        private var name: String? = null
        private var version: String? = null
        private var signers: List<HashValue>? = null

        override fun build(): CPKDependency {
            if (node.hasChildNodes()) {
                val childNodes = node.childNodes
                for (idx in 0 until childNodes.length) {
                    val childNode = childNodes.item(idx)
                    when (childNode.nodeName) {
                        "name" -> name = getTextValue(childNode)
                        "version" -> version = getTextValue(childNode)
                        "signers" -> signers = SignersBuilder(childNode).build()
                    }
                }
            }
            return CPKDependency(
                name = name ?: fail("cpkDependency.name missing"),
                version = version ?: fail("cpkDependency.version missing"),
                signers = signers ?: fail("cpkDependency.signers missing")
            )
        }
    }
}

class CPKDependenciesBuilder(private val node: Node) : AbstractBuilder<List<CPKDependency>> {
    private val dependencies = mutableListOf<CPKDependency>()

    override fun build(): List<CPKDependency> {
        if (node.hasChildNodes()) {
            val childNodes = node.childNodes
            for (idx in 0 until childNodes.length) {
                val childNode = childNodes.item(idx)
                when (childNode.nodeName) {
                    "cpkDependency" ->
                        dependencies.add(CPKDependency.Builder(childNode).build())
                }
            }
        }
        return unmodifiableList(dependencies)
    }
}

class DependencyConstraint(val fileName: String, val hash: HashValue) {
    override fun toString(): String {
        return """<dependencyConstraint>
    <fileName>$fileName</fileName>
    <hash algorithm="${hash.algorithm}">$hash</hash>
</dependencyConstraint>"""
    }

    class Builder(private val node: Node) : AbstractBuilder<DependencyConstraint> {
        private var fileName: String? = null
        private var hash: HashValue? = null

        override fun build(): DependencyConstraint {
            if (node.hasChildNodes()) {
                val childNodes = node.childNodes
                for (idx in 0 until childNodes.length) {
                    val childNode = childNodes.item(idx)
                    when (childNode.nodeName) {
                        "fileName" -> fileName = getTextValue(childNode)
                        "hash" -> hash = HashValue.Builder(childNode).build()
                    }
                }
            }
            return DependencyConstraint(
                fileName = fileName ?: fail("dependencyConstraint.fileName missing"),
                hash = hash ?: fail("dependencyConstraint.hash missing")
            )
        }
    }
}

class DependencyConstraintsBuilder(private val node: Node): AbstractBuilder<List<DependencyConstraint>> {
    private val constraints = mutableListOf<DependencyConstraint>()

    override fun build(): List<DependencyConstraint> {
        if (node.hasChildNodes()) {
            val childNodes = node.childNodes
            for (idx in 0 until childNodes.length) {
                val childNode = childNodes.item(idx)
                when (childNode.nodeName) {
                    "dependencyConstraint" ->
                        constraints.add(DependencyConstraint.Builder(childNode).build())
                }
            }
        }
        return unmodifiableList(constraints)
    }
}

private fun loadDocumentFrom(input: InputStream): Document {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
}

fun loadCPKDependencies(input: InputStream): List<CPKDependency> {
    val nodes = loadDocumentFrom(input).getElementsByTagName("cpkDependencies")
    assertEquals(1, nodes.length)
    return CPKDependenciesBuilder(nodes.item(0)).build()
}

fun loadDependencyConstraints(input: InputStream): List<DependencyConstraint> {
    val nodes = loadDocumentFrom(input).getElementsByTagName("dependencyConstraints")
    assertEquals(1, nodes.length)
    return DependencyConstraintsBuilder(nodes.item(0)).build()
}
