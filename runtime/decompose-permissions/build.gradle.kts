plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
    id("tech.annexflow.lvm.publish")
}

group = "tech.annexflow.lvm"

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
        getByName("androidHostTest").dependencies {
            // Activities that are recreated, with their saved state and retained instances.
            implementation(libs.robolectric)
        }
    }
}
