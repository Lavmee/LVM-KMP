plugins {
    `java-platform`
    id("tech.annexflow.lvm.publish")
}

group = "tech.annexflow.lvm"

// Every published library and the compiler plugin, at the version of this build.
dependencies {
    constraints {
        api(project(":runtime:core"))
        api(project(":runtime:coroutines"))
        api(project(":runtime:decompose"))
        api(project(":runtime:decompose-compose"))
        api(project(":runtime:decompose-fetcher"))
        api(project(":runtime:decompose-permissions"))
        api(project(":runtime:io"))
        api(project(":runtime:logger"))
        api(project(":runtime:logger-console"))
        api(project(":runtime:logger-file"))
        api(project(":runtime:main-context"))
        api(project(":runtime:storage"))
        api(project(":compiler:main-context-compiler"))
    }
}
