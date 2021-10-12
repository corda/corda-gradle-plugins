import net.corda.plugins.Cordapp
import net.corda.plugins.Cordform
import net.corda.plugins.Node
import net.corda.plugins.RpcSettings

plugins {
    id("net.corda.plugins.cordformation")
}

val corda_group: String by project
val corda_release_version: String by project
val slf4j_version: String by project

dependencies {
    cordapp("$corda_group:corda-finance-contracts:$corda_release_version")
    cordapp("$corda_group:corda-finance-workflows:$corda_release_version")
    cordaRuntimeOnly("$corda_group:corda:$corda_release_version")
    cordaRuntimeOnly("$corda_group:corda-node-api:$corda_release_version")
    cordaRuntimeOnly("org.slf4j:slf4j-simple:$slf4j_version")
}

tasks.register<Cordform>("deployNodes") {
    nodeDefaults {
        projectCordapp {
            deploy = false
        }

        cordapp("$corda_group:corda-finance-contracts:$corda_release_version")
        cordapp("$corda_group:corda-finance-workflows:$corda_release_version") {
            config("a=b")
        }
        runSchemaMigration = false
    }

    node {
        name("O=Notary Service,L=Zurich,C=CH")
        p2pPort(60000)

        notary = mapOf("validating" to false)

        rpcSettings {
            address("localhost:60001")
            adminAddress("localhost:60002")
        }
    }

    node {
        name("O=BankOfCorda,L=London,C=GB")
        p2pPort(10000)

        rpcSettings {
            address("localhost:10001")
            adminAddress("localhost:10002")
        }

        rpcUsers = listOf(
            mapOf(
                "user" to "user1",
                "password" to "test",
                "permissions" to listOf("ALL")
            )
        )
    }
}
