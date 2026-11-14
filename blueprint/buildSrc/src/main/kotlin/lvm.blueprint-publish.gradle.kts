// Publishes a blueprint module to Maven Central as lvm-blueprint-<name>, with the group, the version and the POM in the
// root build's gradle.properties. Blueprint can't apply tech.annexflow.lvm.publish from its own sources, so it applies
// vanniktech's plugin itself.

import java.util.Properties

plugins {
    id("com.vanniktech.maven.publish")
}

@Suppress("UnstableApiUsage")
val lvmProperties: Properties = Properties().apply {
    val file = isolated.rootProject.projectDirectory.file("../gradle.properties")
    load(providers.fileContents(file).asText.get().reader())
}

fun lvmProperty(key: String): String =
    checkNotNull(lvmProperties.getProperty(key)) { "Set $key in gradle.properties of the root build." }

val artifactId = "lvm-blueprint-${project.name}"
val projectUrl = lvmProperty("lvm.publish.url")

mavenPublishing {
    coordinates(lvmProperty("GROUP"), artifactId, lvmProperty("VERSION_NAME"))
    // As in tech.annexflow.lvm.publish: vanniktech's own Gradle properties, which reach this included build too, may
    // have set these up already, and -Plvm.publish.signing=false publishes a release version locally without a key.
    if ("publishToMavenCentral" !in tasks.names) publishToMavenCentral(automaticRelease = false)
    val signing = providers.gradleProperty("lvm.publish.signing").orNull != "false"
    if (signing && !pluginManager.hasPlugin("signing")) signAllPublications()
    pom {
        name = artifactId
        description = lvmProperty("lvm.publish.description")
        url = projectUrl
        licenses {
            license {
                name = lvmProperty("lvm.publish.license.name")
                url = lvmProperty("lvm.publish.license.url")
            }
        }
        developers {
            developer {
                id = lvmProperty("lvm.publish.developer.id")
                name = lvmProperty("lvm.publish.developer.name")
                url = lvmProperty("lvm.publish.developer.url")
            }
        }
        scm {
            url = projectUrl
            connection = "scm:git:$projectUrl.git"
            developerConnection = "scm:git:$projectUrl.git"
        }
    }
}
