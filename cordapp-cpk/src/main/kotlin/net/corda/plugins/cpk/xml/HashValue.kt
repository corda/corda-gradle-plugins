package net.corda.plugins.cpk.xml

import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.bind.annotation.XmlValue

class HashValue(
    @field:XmlSchemaType(name = "xsd:base64Binary")
    @field:XmlValue
    @JvmField
    var value: ByteArray,

    @field:XmlAttribute(name = "algorithm", required = true)
    @JvmField
    var algorithm: String
) {
    constructor() : this(ByteArray(size = 0), "")
}
