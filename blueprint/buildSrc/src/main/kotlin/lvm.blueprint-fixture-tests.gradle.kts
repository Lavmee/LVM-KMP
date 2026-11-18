// Tests that run blueprint plugins in TestKit fixture builds.
// Fixtures build blueprint from a copy of its sources, so their nested builds never write into this build's outputs.
// Every module has a copy of its own: the test tasks of several modules run at once, and nested builds of one copy
// would compile into the same outputs at the same time.

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
}

// The copies and the Gradle user home of the fixtures live in blueprint's build directory, or in
// LVM_BLUEPRINT_FIXTURE_DIR, which a CI runner that cleans its checkout before every job points elsewhere.
val fixtureDir: Provider<Directory> = layout.dir(providers.environmentVariable("LVM_BLUEPRINT_FIXTURE_DIR").map(::File))
    .orElse(isolated.rootProject.projectDirectory.dir("build/fixtures"))
val fixtureSourcesPath = "src/${project.name}"

val blueprintFixtureSources = tasks.register<Sync>("blueprintFixtureSources") {
    description = "Copies the blueprint sources that TestKit fixture builds include, isolated from this build's outputs."
    from(rootDir.parentFile) {
        include("blueprint/**", "gradle/libs.versions.toml", "gradle.properties")
        exclude(*BlueprintFixtureDir.BUILD_OUTPUTS)
    }
    into(fixtureDir.map { it.dir(fixtureSourcesPath) })
    // The nested builds keep their outputs, so the next run builds blueprint incrementally.
    preserve { include(*BlueprintFixtureDir.BUILD_OUTPUTS) }
}

tasks.test {
    useJUnitPlatform()
    // Tests that need the Android SDK skip without it, so the log names them.
    testLogging.events(TestLogEvent.SKIPPED)
    jvmArgumentProviders.add(
        objects.newInstance<BlueprintFixtureDir>().apply {
            root.fileProvider(blueprintFixtureSources.map { it.destinationDir })
            // One Gradle user home for the fixtures of every module, so each blueprint build downloads dependencies,
            // transforms them and compiles fixture scripts once, not once per module. Concurrent daemons share it.
            testKitDir.set(fixtureDir.map { it.dir("test-kit") })
        },
    )
}
