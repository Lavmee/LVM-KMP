import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("tech.annexflow.lvm.quality")
}

group = "tech.annexflow.lvm"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // The compiler plugin runs in the Kotlin compiler of the consuming build, on JDK 17 or newer.
        freeCompilerArgs.add("-Xjdk-release=17")
        allWarningsAsErrors = true
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
    // The flat layout of LVM's other modules, which blueprint sets up only for multiplatform modules.
    sourceSets {
        main { kotlin.setSrcDirs(listOf("src")) }
        test { kotlin.setSrcDirs(listOf("test")) }
    }
}

sourceSets {
    main { resources.setSrcDirs(listOf("resources")) }
    test { resources.setSrcDirs(emptyList<String>()) }
}

dependencies {
    // The consuming build's Kotlin compiler provides these classes; the plugin works only with this exact version.
    compileOnly(libs.kotlin.compilerEmbeddable)

    testImplementation(libs.kotlin.compileTesting)
    testImplementation(project(":runtime:main-context"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
