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
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Compile against the JDK 17 API, so blueprint can't call methods that don't exist on the JDK it targets.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesApi(gradleTestKit())
}

val lvmProperties: Provider<Properties> = providers
    .fileContents(layout.projectDirectory.file("../../gradle.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) } }

// The LVM group and version, the versions LVM is tested with, the minimum versions it supports and LVM's own artifacts.
buildConfig {
    packageName("tech.annexflow.lvm.blueprint.common")
    className("LvmBuildInfo")
    useKotlinOutput { internalVisibility = false }

    buildConfigField("VERSION", lvmProperties.map { it.getProperty("VERSION_NAME") })
    buildConfigField("GROUP", lvmProperties.map { it.getProperty("GROUP") })
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
    // LVM artifacts that blueprint adds to projects, with GROUP and VERSION.
    buildConfigField("MAIN_CONTEXT_ARTIFACT", "lvm-main-context")
    buildConfigField("MAIN_CONTEXT_COMPILER_ARTIFACT", "lvm-main-context-compiler")

    // Versions that only the TestKit fixtures need.
    sourceSets.named("testFixtures") {
        packageName("tech.annexflow.lvm.blueprint.testing")
        className("LvmTestVersions")
        useKotlinOutput { internalVisibility = false }

        buildConfigField("DETEKT", libs.versions.detekt)
        buildConfigField("COMPOSE_MULTIPLATFORM", libs.versions.composeMultiplatform)
        buildConfigField("VANNIKTECH", libs.versions.vanniktech)
    }
}

tasks.test {
    useJUnitPlatform()
}
