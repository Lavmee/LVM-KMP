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
    compileOnly(libs.android.gradlePluginApi)

    testImplementation(testFixtures(project(":common")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        register("lvmAndroidApplication") {
            id = "tech.annexflow.lvm.android.application"
            implementationClass = "tech.annexflow.lvm.blueprint.android.LvmAndroidApplicationPlugin"
        }
        register("lvmAndroidLibrary") {
            id = "tech.annexflow.lvm.android.library"
            implementationClass = "tech.annexflow.lvm.blueprint.android.LvmAndroidLibraryPlugin"
        }
        register("lvmAndroidTest") {
            id = "tech.annexflow.lvm.android.test"
            implementationClass = "tech.annexflow.lvm.blueprint.android.LvmAndroidTestPlugin"
        }
    }
}
