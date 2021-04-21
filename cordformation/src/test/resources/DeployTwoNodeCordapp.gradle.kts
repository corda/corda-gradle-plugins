import net.corda.plugins.Cordapp
import net.corda.plugins.Cordform
import net.corda.plugins.Node
import net.corda.plugins.RpcSettings

plugins {
    id("net.corda.plugins.cordformation")
}

apply(from = "repositories.gradle")

val corda_group: String by project
val corda_bundle_version: String by project
val corda_release_version: String by project

val cordaCPK by configurations.creating

dependencies {
    cordapp("$corda_group:corda-finance-contracts:$corda_bundle_version")
    cordapp("$corda_group:corda-finance-workflows:$corda_bundle_version")
    cordaRuntimeOnly("$corda_group:corda-node-api:$corda_bundle_version")
    cordaRuntimeOnly("$corda_group:corda:$corda_release_version")
}

val cpk = tasks.register<Jar>("cpk") {
    archiveBaseName.set("locally-built")
    archiveClassifier.set("cordapp")
    archiveExtension.set("cpk")
}

artifacts {
    add("cordaCPK", cpk)
}

tasks.register<Cordform>("deployNodes") {
    dependsOn.add("cpk")

    nodeDefaults {
        projectCordapp {
            deploy = false
        }

        cordapp("$corda_group:corda-finance-contracts:$corda_bundle_version")
        cordapp("$corda_group:corda-finance-workflows:$corda_bundle_version") {
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

        projectCordapp {
            config("a=b")
            deploy = true
        }
    }
}
