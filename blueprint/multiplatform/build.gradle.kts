import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.android.gradlePluginApi)

    testImplementation(testFixtures(project(":common")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        register("lvmMultiplatform") {
            id = "tech.annexflow.lvm.multiplatform"
            implementationClass = "tech.annexflow.lvm.blueprint.multiplatform.LvmMultiplatformPlugin"
        }
    }
}

// Fixtures build blueprint from a copy of its sources, so their nested builds never write into this build's outputs.
abstract class BlueprintFixtureDir : CommandLineArgumentProvider {
    /** Root of the copy. Its location is not an input. */
    @get:Internal
    abstract val root: DirectoryProperty

    /** The copied sources. Nested fixture builds write their own outputs into the copy, so those are not inputs. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sources: FileTree
        get() = root.asFileTree.matching { exclude(*BUILD_OUTPUTS) }

    override fun asArguments(): Iterable<String> =
        listOf("-Dlvm.blueprint.dir=" + root.get().dir("blueprint").asFile.absolutePath)

    companion object {
        val BUILD_OUTPUTS = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }
}

val blueprintFixtureSources = tasks.register<Sync>("blueprintFixtureSources") {
    from(rootDir.parentFile) {
        include("blueprint/**", "gradle/libs.versions.toml", "gradle.properties")
        exclude(*BlueprintFixtureDir.BUILD_OUTPUTS)
    }
    into(layout.buildDirectory.dir("fixture-src"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("lvm.test.kotlinVersion", libs.versions.kotlin.get())
    systemProperty("lvm.test.agpVersion", libs.versions.agp.get())
    systemProperty("lvm.test.detektVersion", libs.versions.detekt.get())
    jvmArgumentProviders.add(
        objects.newInstance<BlueprintFixtureDir>().apply {
            root.fileProvider(blueprintFixtureSources.map { it.destinationDir })
        },
    )
}
