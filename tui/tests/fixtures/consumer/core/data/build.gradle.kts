plugins {
    alias(lvmLibs.plugins.blueprint.multiplatform)
}

lvm {
    targets { jvm() }
    kotlin { explicitApi = true }
}
