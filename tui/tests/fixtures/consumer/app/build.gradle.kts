plugins {
    alias(lvmLibs.plugins.blueprint.android.application)
    alias(lvmLibs.plugins.blueprint.compose)
}

lvm {
    android { namespace = "com.example.app" }
    application {
        versionCode = 3
        versionName = "1.2.0"
    }
}

dependencies {
    implementation(projects.shared)
    implementation(lvmLibs.lvm.decompose.compose)
}
