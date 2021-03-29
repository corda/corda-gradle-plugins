package net.corda.plugins.cpk.xml

import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "cpkDependencies")
class CPKDependencies(
    @field:XmlElement(name = "cpkDependency", type = CPKDependency::class)
    @JvmField
    var cpkDependencies: List<CPKDependency>?
){
    constructor() : this(null)
}
