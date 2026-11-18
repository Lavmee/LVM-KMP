plugins {
    alias(lvmLibs.plugins.android.application) apply false
    alias(lvmLibs.plugins.android.kmp.library) apply false
    alias(lvmLibs.plugins.kotlin.multiplatform) apply false
    alias(lvmLibs.plugins.kotlin.compose) apply false
    alias(lvmLibs.plugins.compose.multiplatform) apply false
    alias(lvmLibs.plugins.detekt) apply false

    alias(lvmLibs.plugins.blueprint.multiplatform) apply false
    alias(lvmLibs.plugins.blueprint.compose) apply false
    alias(lvmLibs.plugins.blueprint.android.application) apply false
    alias(lvmLibs.plugins.blueprint.quality) apply false
}
