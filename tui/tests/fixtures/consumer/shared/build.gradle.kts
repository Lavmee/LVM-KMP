plugins {
    alias(lvmLibs.plugins.blueprint.multiplatform)
    alias(lvmLibs.plugins.blueprint.compose)
    alias(lvmLibs.plugins.blueprint.quality)
}

group = "com.example"

lvm {
    targets {
        android()
        ios { framework { baseName = "Shared"; isStatic = true } }
        jvm()
    }
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.data)
        implementation(lvmLibs.lvm.decompose)
    }
}
