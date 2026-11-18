pluginManagement {
    repositories {
{{localRepository}}        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("tech.annexflow.lvm.settings") version "{{lvmVersion}}"
}
{{localResolution}}
lvm {
    kotlin { flatLayout = {{flatLayout}} }
}

rootProject.name = "{{name}}"

{{includes}}
