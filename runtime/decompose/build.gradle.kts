plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
}

group = "tech.annexflow.lvm"

// The klib of common code is named after archivesName, without the group. Named after the project, it would be
// "decompose_commonMain" like Decompose's own, and a module that sees both would keep only one of them.
base { archivesName = "lvm-decompose" }

lvm {
    targets {
        android()
        ios()
        jvm()
    }
    android { namespace = "tech.annexflow.lvm.decompose" }
    kotlin { explicitApi = true }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":runtime:logger"))
            api(project(":runtime:main-context"))
            api(libs.androidx.paging.common)
            api(libs.decompose)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)
            implementation(libs.essenty.lifecycle.coroutines)
        }
        androidMain.dependencies {
            // ComponentActivity is in the API of the Android roots.
            api(libs.androidx.activity)
            // Dispatchers.Main on Android, for componentScope.
            implementation(libs.kotlinx.coroutines.android)
        }
        iosMain.dependencies {
            // Saved state as JSON in an NSCoder.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
