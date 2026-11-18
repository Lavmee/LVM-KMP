pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("tech.annexflow.lvm.settings") version "0.3.2"
}

lvm {
    android { minSdk = 26 }
    kotlin {
        flatLayout = true
        optIns.add("kotlin.time.ExperimentalTime")
    }
}

rootProject.name = "Consumer"

include(":app")
include(":shared")
include(":core:data")
