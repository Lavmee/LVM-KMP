import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `java-test-fixtures`
    id("com.github.gmazzo.buildconfig")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesApi(gradleTestKit())
}

val lvmVersion: Provider<String> = providers
    .fileContents(layout.projectDirectory.file("../../gradle.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) }.getProperty("VERSION_NAME") }

// The LVM version, the versions LVM is tested with and the minimum versions it supports.
buildConfig {
    packageName("tech.annexflow.lvm.blueprint.common")
    className("LvmBuildInfo")
    useKotlinOutput { internalVisibility = false }

    buildConfigField("VERSION", lvmVersion)
    buildConfigField("TESTED_GRADLE", gradle.gradleVersion)
    buildConfigField("TESTED_KOTLIN", libs.versions.kotlin)
    buildConfigField("TESTED_AGP", libs.versions.agp)
    buildConfigField(
        "DETEKT_COMPOSE_RULES",
        libs.detekt.composeRules.map { "${it.module}:${it.versionConstraint.requiredVersion}" },
    )
    buildConfigField("MIN_GRADLE", "9.6")
    buildConfigField("MIN_KOTLIN", "2.4.0")
    buildConfigField("MIN_AGP", "9.4.0")
}

tasks.test {
    useJUnitPlatform()
}
