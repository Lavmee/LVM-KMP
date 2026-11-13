plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
    id("tech.annexflow.lvm.compose")
}

group = "tech.annexflow.lvm"

// The klib of common code is named after archivesName, without the group; the artifact ID keeps it apart from the
// klibs of other libraries.
base { archivesName = "lvm-decompose-compose" }

lvm {
    targets {
        // Compose UI tests need Robolectric on the Android host; they run on JVM and the iOS simulator.
        android { hostTests = false }
        ios()
        jvm()
    }
    android { namespace = "tech.annexflow.lvm.decompose.compose" }
    kotlin { explicitApi = true }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.animation.core)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.decompose)
            api(libs.decompose.extensionsCompose)
            api(libs.decompose.extensionsComposeExperimental)
            api(libs.essenty.back.handler)
            implementation(libs.navigationevent.compose)
        }
        commonTest.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui.test)
        }
        jvmTest.dependencies {
            // Skia for the operating system that runs the tests.
            implementation(compose.desktop.currentOs)
        }
    }
}
