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
    // Published libraries load on JDK 17 and newer.
    jvm { target = 17 }
    kotlin {
        warningsAsErrors = true
        // Sources in src, test, src@<name> and test@<name>, without the root package in the path.
        flatLayout = true
    }
}

rootProject.name = "LVM-KMP"

include(":runtime:core")
include(":runtime:coroutines")
include(":runtime:io")
include(":runtime:logger")
include(":runtime:logger-console")
include(":runtime:logger-file")
include(":samples:smoke")
