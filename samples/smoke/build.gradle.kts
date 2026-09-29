plugins {
    id("tech.annexflow.lvm.multiplatform")
    id("tech.annexflow.lvm.quality")
}

// Namespace: tech.annexflow.lvm + :samples:smoke → tech.annexflow.lvm.samples.smoke
group = "tech.annexflow.lvm"

lvm {
    targets {
        android()
        ios {
            framework {
                baseName = "LvmSmoke"
                isStatic = true
                bundleId = "tech.annexflow.lvm.samples.smoke"
            }
        }
        jvm()
    }
}
