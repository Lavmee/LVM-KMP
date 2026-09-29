// Tests that run blueprint plugins in TestKit fixture builds.
// Fixtures build blueprint from a copy of its sources, so their nested builds never write into this build's outputs.

plugins {
    java
}

val blueprintFixtureSources = tasks.register<Sync>("blueprintFixtureSources") {
    from(rootDir.parentFile) {
        include("blueprint/**", "gradle/libs.versions.toml", "gradle.properties")
        exclude(*BlueprintFixtureDir.BUILD_OUTPUTS)
    }
    into(layout.buildDirectory.dir("fixture-src"))
}

// Precompiled script plugins have no `libs` accessor.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(alias: String): String = catalog.findVersion(alias).get().requiredVersion

tasks.test {
    useJUnitPlatform()
    systemProperty("lvm.test.kotlinVersion", catalogVersion("kotlin"))
    systemProperty("lvm.test.agpVersion", catalogVersion("agp"))
    systemProperty("lvm.test.detektVersion", catalogVersion("detekt"))
    jvmArgumentProviders.add(
        objects.newInstance<BlueprintFixtureDir>().apply {
            root.fileProvider(blueprintFixtureSources.map { it.destinationDir })
        },
    )
}
