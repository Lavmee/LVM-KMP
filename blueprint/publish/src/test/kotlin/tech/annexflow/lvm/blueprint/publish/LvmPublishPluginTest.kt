package tech.annexflow.lvm.blueprint.publish

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader
import tech.annexflow.lvm.blueprint.testing.LvmTestVersions

// Fixtures publish to a Maven repository inside the fixture: maven.repo.local moves Maven local there.
class LvmPublishPluginTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `a library publishes its artifact ID and the POM from settings`() {
        val fixture = fixture()

        fixture.build(":lib:publishToMavenLocal", repository())

        val pom = pom("acme-lib").readText()
        assertContains(pom, "<name>acme-lib</name>")
        assertContains(pom, "<description>Libraries of the fixture</description>")
        assertContains(pom, "<url>https://example.org/fixture</url>")
        assertContains(pom, "<name>The Apache License, Version 2.0</name>")
        assertContains(pom, "<id>dev</id>")
        assertContains(pom, "<connection>scm:git:https://example.org/fixture.git</connection>")
        assertTrue(pom("acme-lib-jvm").exists())
        // The jar is named after archivesName, which the klib of common code is named after too.
        assertTrue(dir.resolve("lib/build/libs/acme-lib-jvm-1.0.0-SNAPSHOT.jar").isFile)
    }

    @Test
    fun `the module and a Gradle property win over settings`() {
        val fixture = fixture()
        fixture.file("lib/build.gradle.kts", library("lvm { publish { description = \"From the module\" } }"))

        fixture.build(":lib:publishToMavenLocal", repository(), "-Plvm.publish.url=https://example.org/property")

        val pom = pom("acme-lib").readText()
        assertContains(pom, "<description>From the module</description>")
        assertContains(pom, "<url>https://example.org/property</url>")
        assertContains(pom, "<connection>scm:git:https://example.org/property.git</connection>")
    }

    @Test
    fun `a POM without a developer fails and names the missing keys`() {
        val fixture = fixture(developer = "")

        val result = fixture.buildAndFail(":lib:publishToMavenLocal", repository())

        assertContains(result.output, "lvm.publish.developer.id")
        assertContains(result.output, "lvm.publish.developer.name")
    }

    @Test
    fun `a release is signed unless signing is off`() {
        val fixture = fixture()
        fixture.file("lib/build.gradle.kts", library(version = "1.0.0"))

        val unsigned = fixture.buildAndFail(":lib:publishToMavenLocal", repository())
        assertContains(unsigned.output, "signatory")

        fixture.build(":lib:publishToMavenLocal", repository(), "-Plvm.publish.signing=false")
        assertTrue(pom("acme-lib", "1.0.0").exists())
        assertFalse(pom("acme-lib", "1.0.0").parentFile.listFiles().orEmpty().any { it.name.endsWith(".asc") })
    }

    @Test
    fun `the module publishes to Maven Central with or without vanniktech's own properties`() {
        val fixture = fixture()

        val tasks = fixture.build(":lib:tasks", "--all").output
        assertContains(tasks, "publishToMavenCentral")
        assertContains(tasks, "publishAndReleaseToMavenCentral")

        fixture.build(":lib:tasks", "-PmavenCentralPublishing=true", "-PsignAllPublications=true")
    }

    @Test
    fun `check fails until updateKotlinAbi writes the dump`() {
        val fixture = fixture()

        fixture.buildAndFail(":lib:check")
        fixture.build(":lib:updateKotlinAbi")
        fixture.build(":lib:check")

        assertContains(dir.resolve("lib/api").walk().single { it.isFile }.readText(), "Visible")
    }

    @Test
    fun `excluded names and annotations stay out of the dump`() {
        val fixture = fixture(abi = "abi { excludedNames.add(\"com.example.Hidden\") }")
        fixture.file("lib/build.gradle.kts", library("lvm { publish { abi { excludedAnnotations.add(\"com.example.Internal\") } } }"))

        fixture.build(":lib:updateKotlinAbi")

        val dump = dir.resolve("lib/api").walk().single { it.isFile }.readText()
        assertContains(dump, "Visible")
        assertFalse(dump.contains("Hidden"))
        assertFalse(dump.contains("Annotated"))
    }

    @Test
    fun `ABI validation stays off when the build turns it off`() {
        val fixture = fixture()

        fixture.build(":lib:check", "-Plvm.publish.abi.enabled=false")

        assertFalse(dir.resolve("lib/api").exists())
    }

    @Test
    fun `a Kotlin JVM module publishes with the artifact ID`() {
        val fixture = fixture()
        fixture.file(
            "tool/build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.jvm")
                id("tech.annexflow.lvm.publish")
            }
            group = "com.example"
            version = "1.0.0-SNAPSHOT"
            """,
        )
        fixture.file("tool/src/main/kotlin/Tool.kt", "class Tool")

        fixture.build(":tool:publishToMavenLocal", repository())

        assertTrue(pom("acme-tool").exists())
    }

    private fun repository(): String = "-Dmaven.repo.local=${dir.resolve("m2").absolutePath}"

    private fun pom(artifactId: String, version: String = "1.0.0-SNAPSHOT"): File =
        dir.resolve("m2/com/example/$artifactId/$version/$artifactId-$version.pom")

    private fun fixture(
        developer: String = "developer { id = \"dev\"; name = \"Developer\" }",
        abi: String = "",
    ): GradleFixture = GradleFixture(dir).apply {
        file(
            "settings.gradle.kts",
            settingsHeader(
                """
                catalog = false
                publish {
                    artifactIdPrefix = "acme-"
                    description = "Libraries of the fixture"
                    url = "https://example.org/fixture"
                    license { name = "The Apache License, Version 2.0"; url = "https://www.apache.org/licenses/LICENSE-2.0.txt" }
                    $developer
                    $abi
                }
                """.trimIndent(),
            ) + "\nrootProject.name = \"fixture\"\ninclude(\":lib\", \":tool\")",
        )
        file(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("org.jetbrains.kotlin.jvm") version "$kotlinVersion" apply false
                id("com.vanniktech.maven.publish") version "${LvmTestVersions.VANNIKTECH}" apply false
                id("tech.annexflow.lvm.multiplatform") apply false
                id("tech.annexflow.lvm.publish") apply false
            }
            """,
        )
        file("lib/build.gradle.kts", library())
        file(
            "lib/src/commonMain/kotlin/Api.kt",
            """
            package com.example

            annotation class Internal

            class Visible

            class Hidden

            @Internal
            class Annotated
            """,
        )
        file("tool/build.gradle.kts", "")
    }

    /** A Kotlin Multiplatform module for JVM with the publish plugin and [extra] lines. */
    private fun library(extra: String = "", version: String = "1.0.0-SNAPSHOT"): String = """
        plugins {
            id("tech.annexflow.lvm.multiplatform")
            id("tech.annexflow.lvm.publish")
        }
        group = "com.example"
        version = "$version"
        lvm { targets { jvm() } }
        $extra
    """
}
