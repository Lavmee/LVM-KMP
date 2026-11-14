plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
    id("tech.annexflow.lvm.publish")
}

group = "tech.annexflow.lvm"

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
