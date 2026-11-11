plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
    // The tests store @Serializable classes.
    alias(libs.plugins.kotlin.serialization)
}

group = "tech.annexflow.lvm"

lvm {
    targets {
        android()
        ios()
        jvm()
    }
    android { namespace = "tech.annexflow.lvm.storage" }
    kotlin { explicitApi = true }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":runtime:io"))
            api(libs.androidx.datastore.core)
            api(libs.androidx.datastore.core.okio)
            api(libs.kotlinx.serialization.json)
            api(libs.okio)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.protobuf)
        }
    }
}
