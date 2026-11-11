plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
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
            api(libs.decompose)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.essenty.lifecycle.coroutines)
        }
        androidMain.dependencies {
            // Dispatchers.Main on Android, for componentScope.
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
