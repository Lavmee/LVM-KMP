import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    implementation(project(":common"))

    testImplementation(testFixtures(project(":common")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        register("lvmSettings") {
            id = "tech.annexflow.lvm.settings"
            implementationClass = "tech.annexflow.lvm.blueprint.settings.LvmSettingsPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("lvm.blueprint.dir", rootDir.absolutePath)
    systemProperty("lvm.test.kotlinVersion", libs.versions.kotlin.get())
    systemProperty("lvm.test.agpVersion", libs.versions.agp.get())
    systemProperty("lvm.test.detektVersion", libs.versions.detekt.get())
    // Fixtures build blueprint from its sources, so any blueprint source change must rerun these tests.
    inputs.files(fileTree(rootDir) { include("*/src/main/**", "*/build.gradle.kts", "settings.gradle.kts") })
        .withPropertyName("blueprintSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
