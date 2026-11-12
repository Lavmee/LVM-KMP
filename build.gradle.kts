plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.detekt) apply false
    id("tech.annexflow.lvm.multiplatform") apply false
    id("tech.annexflow.lvm.quality") apply false
    id("tech.annexflow.lvm.main-context") apply false
    id("tech.annexflow.lvm.compose") apply false
}
