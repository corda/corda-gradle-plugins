package net.corda.plugins

import org.gradle.api.tasks.Input

open class Info {
    /** to Corda 3 attributes */
    @get:Input
    var name: String? = null
    @get:Input
    var version: String? = null
    @get:Input
    var vendor: String? = null
    /** Corda 4 attributes */
    @get:Input
    var cordappContractName: String? = null
    @get:Input
    var cordappContractVersion: String? = null
    @get:Input
    var cordappContractVendor: String? = null
    @get:Input
    var cordappContractLicence: String? = null
    @get:Input
    var cordappWorflowName: String? = null
    @get:Input
    var cordappWorflowVersion: String? = null
    @get:Input
    var cordappWorflowVendor: String? = null
    @get:Input
    var cordappWorflowLicence: String? = null
    @get:Input
    var targetPlatformVersion: Int? = null
    @get:Input
    var minimumPlatformVersion: Int? = null
}