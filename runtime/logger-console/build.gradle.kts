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
    android { namespace = "tech.annexflow.lvm.logger.console" }
    kotlin { explicitApi = true }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":runtime:logger"))
            implementation(libs.kermit)
        }
    }
}
