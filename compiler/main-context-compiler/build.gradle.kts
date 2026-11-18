plugins {
    id("tech.annexflow.lvm.jvm")
    id("tech.annexflow.lvm.quality")
    id("tech.annexflow.lvm.publish")
}

group = "tech.annexflow.lvm"

lvm {
    kotlin {
        // The compiler plugin runs in the Kotlin compiler of the consuming build, on JDK 17 or newer.
        freeCompilerArgs.add("-Xjdk-release=17")
        optIns.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

dependencies {
    // The consuming build's Kotlin compiler provides these classes; the plugin works only with this exact version.
    compileOnly(libs.kotlin.compilerEmbeddable)

    testImplementation(libs.kotlin.compileTesting)
    testImplementation(project(":runtime:main-context"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
