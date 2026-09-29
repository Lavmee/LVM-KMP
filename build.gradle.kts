plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.detekt) apply false
    id("tech.annexflow.lvm.multiplatform") apply false
    id("tech.annexflow.lvm.quality") apply false
}
