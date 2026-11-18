// Tests that run blueprint plugins in TestKit fixture builds.
// Fixtures build blueprint from a copy of its sources, so their nested builds never write into this build's outputs.

plugins {
    java
}

val blueprintFixtureSources = tasks.register<Sync>("blueprintFixtureSources") {
    description = "Copies the blueprint sources that TestKit fixture builds include, isolated from this build's outputs."
    from(rootDir.parentFile) {
        include("blueprint/**", "gradle/libs.versions.toml", "gradle.properties")
        exclude(*BlueprintFixtureDir.BUILD_OUTPUTS)
    }
    into(layout.buildDirectory.dir("fixture-src"))
}

tasks.test {
    useJUnitPlatform()
    jvmArgumentProviders.add(
        objects.newInstance<BlueprintFixtureDir>().apply {
            root.fileProvider(blueprintFixtureSources.map { it.destinationDir })
            // One Gradle user home for the fixtures of every module, so each blueprint build downloads dependencies,
            // transforms them and compiles fixture scripts once, not once per module. Concurrent daemons share it.
            testKitDir.set(isolated.rootProject.projectDirectory.dir("build/fixture-test-kit"))
        },
    )
}
