import net.corda.plugins.Cordapp
import net.corda.plugins.Cordform
import net.corda.plugins.Node
import net.corda.plugins.RpcSettings

plugins {
    id("net.corda.plugins.cordformation")
    id("net.corda.plugins.cordapp")
}

apply(from = "repositories.gradle")

val corda_group: String by project
val corda_release_version: String by project
val slf4j_version: String by project
val projectCordappBaseName: String by project
val projectCordappVersion: String by project

cordapp {
    targetPlatformVersion.set(100)
    contract {
        name.set(projectCordappBaseName)
        versionId.set(1)
        licence.set("Test Licence")
        vendor.set("R3 Ltd")
    }
}

dependencies {
    cordapp("$corda_group:corda-finance-contracts:$corda_release_version")
    cordapp("$corda_group:corda-finance-workflows:$corda_release_version")
    cordaRuntime("$corda_group:corda:$corda_release_version")
    cordaRuntime("$corda_group:corda-node-api:$corda_release_version")
    cordaRuntime("org.slf4j:slf4j-simple:$slf4j_version")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(projectCordappBaseName)
    archiveVersion.set(projectCordappVersion)
}

tasks.register<Cordform>("deployNodes") {
    dependsOn.add("jar")

    nodeDefaults {
        projectCordapp(closureOf<Cordapp> {
            deploy = false
        })

        cordapp("$corda_group:corda-finance-contracts:$corda_release_version")
        cordapp("$corda_group:corda-finance-workflows:$corda_release_version", closureOf<Cordapp> {
            config("a=b")
        })
        runSchemaMigration = false
    }

    node(closureOf<Node> {
        name("O=Notary Service,L=Zurich,C=CH")
        p2pPort(60000)

        notary = mapOf("validating" to false)

        rpcSettings(closureOf<RpcSettings> {
            address("localhost:60001")
            adminAddress("localhost:60002")
        })
    })

    node(closureOf<Node> {
        name("O=BankOfCorda,L=London,C=GB")
        p2pPort(10000)

        rpcSettings(closureOf<RpcSettings> {
            address("localhost:10001")
            adminAddress("localhost:10002")
        })

        rpcUsers = listOf(
            mapOf(
                "user" to "user1",
                "password" to "test",
                "permissions" to listOf("ALL")
            )
        )

        projectCordapp(closureOf<Cordapp> {
            config("a = b")
            deploy = true
        })
    })
}
