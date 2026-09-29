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
        },
    )
}
