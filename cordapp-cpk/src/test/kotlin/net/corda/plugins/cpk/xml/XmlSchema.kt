@file:JvmName("XmlSchema")
package net.corda.plugins.cpk.xml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.InputStream
import java.util.Base64
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

class CPKDependency(
    val name: String,
    val version: String,
    val signedBy: HashValue
) {
    class Builder(private val node: Node) : AbstractBuilder<CPKDependency> {
        private var name: String? = null
        private var version: String? = null
        private var signedBy: HashValue? = null

        override fun build(): CPKDependency {
            if (node.hasChildNodes()) {
                val childNodes = node.childNodes
                for (idx in 0 until childNodes.length) {
                    val childNode = childNodes.item(idx)
                    when (childNode.nodeName) {
                        "name" -> name = getTextValue(childNode)
                        "version" -> version = getTextValue(childNode)
                        "signedBy" -> signedBy = HashValue.Builder(childNode).build()
                    }
                }
            }
            return CPKDependency(
                name = name ?: fail("cpkDependency.name missing"),
                version = version ?: fail("cpkDependency.version missing"),
                signedBy = signedBy ?: fail("cpkDependency.signedBy missing")
            )
        }
    }
}

class CPKDependencies(val cpkDependencies: List<CPKDependency>) {
    class Builder(private val node: Node) : AbstractBuilder<CPKDependencies> {
        private val dependencies = mutableListOf<CPKDependency>()

        override fun build(): CPKDependencies {
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
            return CPKDependencies(dependencies)
        }
    }
}

class DependencyConstraint(val fileName: String, val hash: HashValue) {
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

class DependencyConstraints(val constraints: List<DependencyConstraint>) {
    class Builder(private val node: Node): AbstractBuilder<DependencyConstraints> {
        private val constraints = mutableListOf<DependencyConstraint>()

        override fun build(): DependencyConstraints {
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
            return DependencyConstraints(constraints)
        }
    }
}

private fun loadDocumentFrom(input: InputStream): Document {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
}

fun loadCPKDependencies(input: InputStream): CPKDependencies {
    val nodes = loadDocumentFrom(input).getElementsByTagName("cpkDependencies")
    assertEquals(1, nodes.length)
    return CPKDependencies.Builder(nodes.item(0)).build()
}

fun loadDependencyConstraints(input: InputStream): DependencyConstraints {
    val nodes = loadDocumentFrom(input).getElementsByTagName("dependencyConstraints")
    assertEquals(1, nodes.length)
    return DependencyConstraints.Builder(nodes.item(0)).build()
}
