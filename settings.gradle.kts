pluginManagement {
    includeBuild("blueprint")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("tech.annexflow.lvm.settings")
}

lvm {
    // lvm-versions is published from this repository; the build uses gradle/libs.versions.toml as `libs` instead.
    catalog = false
    kotlin { warningsAsErrors = true }
}

rootProject.name = "lvm-kmp"

include(":samples:smoke")
