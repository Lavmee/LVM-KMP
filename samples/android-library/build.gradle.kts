plugins {
    id("tech.annexflow.lvm.android.library")
    id("tech.annexflow.lvm.quality")
}

// The namespace is derived from the group and the path: tech.annexflow.lvm.samples.android_library.
group = "tech.annexflow.lvm"

lvm {
    kotlin { explicitApi = true }
}

android {
    flavorDimensions += "tier"
    productFlavors {
        register("free") { dimension = "tier" }
        register("paid") { dimension = "tier" }
    }
}
