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
    android { namespace = "tech.annexflow.lvm.core" }
    kotlin { explicitApi = true }
}
