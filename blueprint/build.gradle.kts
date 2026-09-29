plugins {
    // Loaded here so every blueprint project shares one plugin class loader,
    // and the Kotlin Gradle plugin of `kotlin-dsl` is loaded once rather than once more for :common.
    id("com.github.gmazzo.buildconfig") apply false
}
