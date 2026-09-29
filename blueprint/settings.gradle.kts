pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    // Kept out of gradle/libs.versions.toml: that catalog is published as lvm-versions.
    plugins {
        id("com.github.gmazzo.buildconfig") version "6.1.2"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "blueprint"

include(":common")
include(":settings")
include(":multiplatform")
include(":quality")
