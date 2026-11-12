plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
    id("tech.annexflow.lvm.compose")
}

// Namespace: tech.annexflow.lvm + :samples:compose-sample -> tech.annexflow.lvm.samples.compose_sample
group = "tech.annexflow.lvm"

lvm {
    targets {
        // Compose UI tests need Robolectric on the Android host; they run on JVM and the iOS simulator.
        android { hostTests = false }
        ios()
        jvm()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.compose.ui.test)
        }
        jvmTest.dependencies {
            // Skia for the operating system that runs the tests.
            implementation(compose.desktop.currentOs)
        }
    }
}
