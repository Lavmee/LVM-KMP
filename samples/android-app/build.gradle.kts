plugins {
    id("tech.annexflow.lvm.android.application")
    id("tech.annexflow.lvm.quality")
    // The Compose compiler for the app's own Compose code; tech.annexflow.lvm.compose is for Kotlin Multiplatform modules.
    alias(libs.plugins.kotlin.compose)
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

dependencies {
    implementation(project(":runtime:decompose"))
    implementation(project(":runtime:decompose-compose"))
    implementation(project(":runtime:logger-console"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
