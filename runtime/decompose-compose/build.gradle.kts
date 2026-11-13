import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

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
    // Code that only Android needs has a no-op for the other targets in one source set. Code for iOS alone would sit
    // in iosMain, whose metadata compilation sees two builds of lifecycle, savedstate and navigationevent with
    // Compose Multiplatform 1.12.2 and warns about their klibs.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withJvm()
                group("ios") { withIos() }
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.animation)
            api(libs.compose.animation.core)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.composeUnstyled.modalBottomSheet)
            api(libs.decompose)
            api(libs.decompose.extensionsCompose)
            api(libs.decompose.extensionsComposeExperimental)
            api(libs.essenty.back.handler)
            implementation(libs.compose.foundation)
            implementation(libs.composeUnstyled.modal)
            implementation(libs.navigationevent.compose)
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
