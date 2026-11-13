package tech.annexflow.lvm.blueprint.publish

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.plugins.signing.Sign
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

/**
 * Publishing to Maven Central with vanniktech's plugin: the artifact ID, the POM from `lvm { publish { } }` and
 * signing.
 */
class LvmPublishPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        val values = LvmValues(project)
        // vanniktech takes the artifact ID as a plain string, so its build-wide prefix is read when the plugin is applied.
        val artifactId = values.raw(LvmKeys.PUBLISH_ARTIFACT_ID_PREFIX)
            .getOrElse(LvmDefaults.PUBLISH_ARTIFACT_ID_PREFIX) + project.name
        val section = project.lvm.extensions.create("publish", PublishSection::class.java)
        section.applyConventions(values, artifactId)

        project.pluginManager.apply("com.vanniktech.maven.publish")
        val publishing = project.extensions.getByType(MavenPublishBaseExtension::class.java)
        publishing.coordinates(artifactId = artifactId)
        // vanniktech's own Gradle properties may have set these up already, and it allows each only once.
        if ("publishToMavenCentral" !in project.tasks.names) publishing.publishToMavenCentral(automaticRelease = false)
        if (!project.pluginManager.hasPlugin("signing")) publishing.signAllPublications()
        val signing = section.signing
        project.tasks.withType(Sign::class.java).configureEach {
            onlyIf("lvm.publish.signing is true") { signing.get() }
        }
        publishing.pom { configure(section) }
        requirePomFields(project, section)

        // The klib of common code is named after archivesName, so it gets the artifact ID too.
        project.pluginManager.withPlugin("base") {
            project.extensions.getByType(BasePluginExtension::class.java).archivesName.set(artifactId)
        }
    }

    private fun PublishSection.applyConventions(values: LvmValues, artifactId: String) {
        name.convention(artifactId)
        description.convention(values.raw(LvmKeys.PUBLISH_DESCRIPTION))
        url.convention(values.raw(LvmKeys.PUBLISH_URL))
        signing.convention(values.boolean(LvmKeys.PUBLISH_SIGNING, LvmDefaults.PUBLISH_SIGNING))
        license.name.convention(values.raw(LvmKeys.PUBLISH_LICENSE_NAME))
        license.url.convention(values.raw(LvmKeys.PUBLISH_LICENSE_URL))
        developer.id.convention(values.raw(LvmKeys.PUBLISH_DEVELOPER_ID))
        developer.name.convention(values.raw(LvmKeys.PUBLISH_DEVELOPER_NAME))
        developer.url.convention(values.raw(LvmKeys.PUBLISH_DEVELOPER_URL))
        scm.url.convention(values.raw(LvmKeys.PUBLISH_SCM_URL).orElse(url))
        scm.connection.convention(values.raw(LvmKeys.PUBLISH_SCM_CONNECTION).orElse(scm.url.map { "scm:git:$it.git" }))
        scm.developerConnection.convention(values.raw(LvmKeys.PUBLISH_SCM_DEVELOPER_CONNECTION).orElse(scm.connection))
    }

    private fun MavenPom.configure(section: PublishSection) {
        name.set(section.name)
        description.set(section.description)
        url.set(section.url)
        licenses {
            license {
                name.set(section.license.name)
                url.set(section.license.url)
            }
        }
        developers {
            developer {
                id.set(section.developer.id)
                name.set(section.developer.name)
                url.set(section.developer.url)
            }
        }
        scm {
            url.set(section.scm.url)
            connection.set(section.scm.connection)
            developerConnection.set(section.scm.developerConnection)
        }
    }

    // Maven Central rejects a POM without these, but only after the upload; failing here says what to set.
    private fun requirePomFields(project: Project, section: PublishSection) {
        val required: List<Pair<String, Provider<String>>> = listOf(
            LvmKeys.PUBLISH_DESCRIPTION to section.description,
            LvmKeys.PUBLISH_URL to section.url,
            LvmKeys.PUBLISH_LICENSE_NAME to section.license.name,
            LvmKeys.PUBLISH_LICENSE_URL to section.license.url,
            LvmKeys.PUBLISH_DEVELOPER_ID to section.developer.id,
            LvmKeys.PUBLISH_DEVELOPER_NAME to section.developer.name,
        )
        val missing = project.providers.provider { required.filterNot { (_, value) -> value.isPresent }.map { it.first } }
        val path = project.path
        project.tasks.withType(GenerateMavenPom::class.java).configureEach {
            doFirst {
                val keys = missing.get()
                check(keys.isEmpty()) {
                    "The POM of $path needs ${keys.joinToString { "lvm.$it" }}: set them in lvm { publish { } } of " +
                        "settings.gradle.kts or of the module."
                }
            }
        }
    }

}
