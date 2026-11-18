plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    id("tech.annexflow.lvm.multiplatform") apply false
    id("tech.annexflow.lvm.jvm") apply false
    id("tech.annexflow.lvm.quality") apply false
    id("tech.annexflow.lvm.main-context") apply false
    id("tech.annexflow.lvm.compose") apply false
    id("tech.annexflow.lvm.publish") apply false
    id("tech.annexflow.lvm.android.application") apply false
    id("tech.annexflow.lvm.android.library") apply false
    id("tech.annexflow.lvm.android.test") apply false
}
