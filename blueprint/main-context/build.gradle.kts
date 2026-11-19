import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    id("lvm.blueprint-fixture-tests")
    id("lvm.blueprint-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Compile against the JDK 17 API, so blueprint can't call methods that don't exist on the JDK it targets.
        freeCompilerArgs.add("-Xjdk-release=17")
        optIn.add("tech.annexflow.lvm.blueprint.common.InternalBlueprintApi")
    }
}

dependencies {
    api(project(":common"))
    compileOnly(libs.kotlin.gradlePlugin)

    testImplementation(testFixtures(project(":common")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// A fixture runs tests with the compiler plugin, so it builds the plugin and the annotation from their sources.
tasks.named<Sync>("blueprintFixtureSources") {
    from(rootDir.parentFile) {
        include("compiler/main-context-compiler/src/**", "runtime/main-context/src/**")
    }
}

gradlePlugin {
    plugins {
        register("lvmMainContext") {
            id = "tech.annexflow.lvm.main-context"
            implementationClass = "tech.annexflow.lvm.blueprint.maincontext.LvmMainContextPlugin"
        }
    }
}
