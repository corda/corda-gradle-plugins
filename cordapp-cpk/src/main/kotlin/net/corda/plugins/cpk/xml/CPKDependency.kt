package net.corda.plugins.cpk.xml

import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlType

@XmlType(propOrder = [ "name", "version", "signedBy" ])
class CPKDependency(
    @field:XmlElement(name = "name")
    @JvmField
    var name: String?,

    @field:XmlElement(name = "version")
    @JvmField
    var version: String?,

    @field:XmlElement(name = "signedBy", required = true)
    @JvmField
    var signedBy: HashValue
) {
    @Suppress("unused")
    constructor() : this(null, null, HashValue())
}
