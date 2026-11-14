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
    android { namespace = "tech.annexflow.lvm.decompose.fetcher" }
    kotlin { explicitApi = true }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":runtime:core"))
            api(project(":runtime:decompose"))
            api(libs.kotlin.retry)
            api(libs.kotlinx.io.core)
            api(libs.ktor.client.core)
            implementation(project(":runtime:coroutines"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
