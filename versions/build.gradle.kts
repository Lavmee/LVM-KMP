plugins {
    `version-catalog`
    id("tech.annexflow.lvm.publish")
}

group = "tech.annexflow.lvm"

val lvmGroup: String = providers.gradleProperty("GROUP").get()

// The versions LVM is tested with, plus LVM's own libraries, BOM and blueprint plugins at the version of this build.
catalog {
    versionCatalog {
        from(files("../gradle/libs.versions.toml"))
        version("lvm", providers.gradleProperty("VERSION_NAME").get())
        listOf(
            "core",
            "coroutines",
            "decompose",
            "decompose-compose",
            "decompose-fetcher",
            "decompose-permissions",
            "io",
            "logger",
            "logger-console",
            "logger-file",
            "main-context",
            "main-context-compiler",
            "storage",
            "bom",
        ).forEach { name -> library("lvm-$name", lvmGroup, "lvm-$name").versionRef("lvm") }
        listOf("multiplatform", "quality", "main-context", "compose", "publish").forEach { name ->
            plugin("blueprint-$name", "tech.annexflow.lvm.$name").versionRef("lvm")
        }
        plugin("blueprint-android-application", "tech.annexflow.lvm.android.application").versionRef("lvm")
        plugin("blueprint-android-library", "tech.annexflow.lvm.android.library").versionRef("lvm")
    }
}
