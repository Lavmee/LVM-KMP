plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
}

group = "tech.annexflow.lvm"

// The klib of common code is named after archivesName, without the group; the artifact ID keeps it apart from the
// klibs of other libraries.
base { archivesName = "lvm-decompose-permissions" }

lvm {
    // moko-permissions publishes Android and iOS variants only.
    targets {
        android()
        ios()
    }
    android { namespace = "tech.annexflow.lvm.decompose.permissions" }
    kotlin { explicitApi = true }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":runtime:decompose"))
            api(libs.moko.permissions)
        }
        androidMain.dependencies {
            // ComponentActivity is in the API; moko-permissions keeps it off the compile classpath.
            api(libs.androidx.activity)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
