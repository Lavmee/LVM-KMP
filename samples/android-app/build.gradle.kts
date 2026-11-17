plugins {
    id("tech.annexflow.lvm.android.application")
    id("tech.annexflow.lvm.quality")
    id("tech.annexflow.lvm.compose")
    alias(libs.plugins.kotlin.serialization)
}

lvm {
    // The application ID defaults to the namespace.
    android { namespace = "tech.annexflow.lvm.samples.android" }
    application {
        versionCode = 1
        versionName = "1.0"
    }
}

android {
    // The sample library has a tier dimension that the app doesn't have.
    defaultConfig { missingDimensionStrategy("tier", "free") }
}

dependencies {
    implementation(project(":samples:android-library"))
    implementation(project(":runtime:decompose"))
    implementation(project(":runtime:decompose-compose"))
    implementation(project(":runtime:logger-console"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
