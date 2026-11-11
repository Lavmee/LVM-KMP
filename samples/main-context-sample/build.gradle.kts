plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
    id("tech.annexflow.lvm.main-context")
}

// Namespace: tech.annexflow.lvm + :samples:main-context-sample -> tech.annexflow.lvm.samples.main_context_sample
group = "tech.annexflow.lvm"

lvm {
    targets {
        android()
        ios()
        jvm()
    }
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// The plugin adds LVM's published artifacts; inside LVM's own build, they come from its modules.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("tech.annexflow.lvm:lvm-main-context")).using(project(":runtime:main-context"))
        substitute(module("tech.annexflow.lvm:lvm-main-context-compiler")).using(project(":compiler:main-context-compiler"))
    }
}
