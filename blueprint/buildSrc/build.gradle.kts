plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // vanniktech's plugin looks for the Kotlin plugin's classes in its own class loader, so both load here, in the
    // class loader of every blueprint project. The Kotlin plugin is the one that kotlin-dsl applies.
    implementation(libs.vanniktech.mavenPublishGradlePlugin)
    implementation(kotlin("gradle-plugin", embeddedKotlinVersion))
}
