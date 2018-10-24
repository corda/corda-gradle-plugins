package net.corda.plugins

import org.gradle.api.tasks.Input

open class Info {
    @Input
    var name: String? = null
    @Input
    var version: String? = null
    @Input
    var vendor: String? = null
    @Input
    var targetPlatformVersion: Int? = null
    @Input
    var minimumPlatformVersion: Int? = null
}