package net.corda.plugins

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Nested
import javax.inject.Inject

open class CordappExtension @Inject constructor(objectFactory: ObjectFactory)  {

    /**
     * CorDapp distribution information.
     */
    @get:Nested
    val info: Info = objectFactory.newInstance(Info::class.java)

    /**
     * Optional parameters for ANT signJar tasks to sign Cordapps.
     */
    @get:Nested
    val signing: Signing = objectFactory.newInstance(Signing::class.java)

    /**
     * Optional marker to seal all packages in the JAR.
     */
    @get:Nested
    val sealing: Sealing = objectFactory.newInstance(Sealing::class.java)


    fun info(action: Action<in Info>) {
        action.execute(info)
    }

    fun signing(action: Action<in Signing>) {
        action.execute(signing)
    }

    fun sealing(action: Action<in Sealing>) {
        action.execute(sealing)
    }
}