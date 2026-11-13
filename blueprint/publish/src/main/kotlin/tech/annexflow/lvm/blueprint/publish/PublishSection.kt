package tech.annexflow.lvm.blueprint.publish

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/** `lvm { publish { } }`: the POM and signing of a published module. */
abstract class PublishSection @Inject constructor(objects: ObjectFactory) {
    /** The name in the POM. Defaults to the artifact ID. */
    abstract val name: Property<String>

    abstract val description: Property<String>

    /** The project's web page, such as its repository. */
    abstract val url: Property<String>

    /** Signs the publications. A release can't be published to Maven Central unsigned. */
    abstract val signing: Property<Boolean>

    val license: PomLicense = objects.newInstance(PomLicense::class.java)
    val developer: PomDeveloper = objects.newInstance(PomDeveloper::class.java)
    val scm: PomScm = objects.newInstance(PomScm::class.java)

    fun license(action: Action<PomLicense>) = action.execute(license)
    fun developer(action: Action<PomDeveloper>) = action.execute(developer)
    fun scm(action: Action<PomScm>) = action.execute(scm)
}

abstract class PomLicense {
    abstract val name: Property<String>
    abstract val url: Property<String>
}

abstract class PomDeveloper {
    abstract val id: Property<String>
    abstract val name: Property<String>
    abstract val url: Property<String>
}

/** Defaults to [PublishSection.url], with `scm:git:<url>.git` as both connections. */
abstract class PomScm {
    abstract val url: Property<String>
    abstract val connection: Property<String>
    abstract val developerConnection: Property<String>
}
