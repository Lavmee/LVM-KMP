package tech.annexflow.lvm.blueprint.android

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class LvmAndroidApplicationPluginTest {

    @TempDir
    lateinit var dir: File

    @BeforeTest
    fun requireAndroidSdk() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
    }

    @Test
    fun `uses default SDK levels, derives the namespace and sets up release and tests`() {
        val fixture = applicationFixture(dir, PRINT_DSL)

        val output = fixture.build(":app:help").output

        assertContains(output, "lvm-dsl namespace=com.example.app applicationId=null compileSdk=37 minSdk=24 targetSdk=37 versionCode=null versionName=null runner=androidx.test.runner.AndroidJUnitRunner")
        assertContains(
            output,
            "lvm-dsl desugaring=true minify=true shrink=true proguard=[$DEFAULT_KEEP_RULES] " +
                "animationsDisabled=true excludes=[/META-INF/DEPENDENCIES, /META-INF/INDEX.LIST]",
        )
    }

    @Test
    fun `module values win over Gradle properties, which win over settings values`() {
        val fixture = applicationFixture(
            dir,
            """
            lvm {
                android { targetSdk = 36 }
                application { applicationId = "com.example.custom" }
            }
            """ + PRINT_DSL,
            settingsLvm = "catalog = false\nandroid { minSdk = 26; targetSdk = 35; testInstrumentationRunner = \"com.example.SettingsRunner\" }\napplication { versionCode = 2; versionName = \"1.0\" }",
        )

        val output = fixture.build(":app:help", "-Plvm.application.versionName=2.0").output

        assertContains(output, "lvm-dsl namespace=com.example.app applicationId=com.example.custom compileSdk=37 minSdk=26 targetSdk=36 versionCode=2 versionName=2.0 runner=com.example.SettingsRunner")
    }

    @Test
    fun `native DSL values win`() {
        val fixture = applicationFixture(
            dir,
            """
            android {
                namespace = "com.example.nativedsl"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.example.id"
                    minSdk = 30
                    targetSdk = 35
                    versionCode = 3
                    testInstrumentationRunner = "com.example.NativeRunner"
                }
                buildTypes.getByName("release").proguardFiles("custom-rules.pro")
                testOptions.animationsDisabled = false
                packaging.resources.excludes.remove("/META-INF/DEPENDENCIES")
            }
            lvm {
                android { testInstrumentationRunner = "com.example.LvmRunner" }
                application { versionCode = 9; versionName = "9.0" }
            }
            """ + PRINT_DSL,
        )

        val output = fixture.build(":app:help").output

        assertContains(output, "lvm-dsl namespace=com.example.nativedsl applicationId=com.example.id compileSdk=36 minSdk=30 targetSdk=35 versionCode=3 versionName=9.0 runner=com.example.NativeRunner")
        assertContains(output, "proguard=[custom-rules.pro] animationsDisabled=false excludes=[/META-INF/INDEX.LIST]")
    }

    @Test
    fun `native preview SDK levels are left alone`() {
        val fixture = applicationFixture(
            dir,
            """
            android {
                compileSdkPreview = "LvmPreview"
                defaultConfig {
                    minSdk { version = preview("LvmPreview") }
                    targetSdkPreview = "LvmPreview"
                }
            }
            androidComponents.finalizeDsl { android ->
                @Suppress("DEPRECATION")
                val minSdkPreview = android.defaultConfig.minSdkPreview
                println(
                    "lvm-sdk compileSdk=" + android.compileSdk + "/" + android.compileSdkPreview +
                        " minSdk=" + android.defaultConfig.minSdk + "/" + minSdkPreview +
                        " targetSdk=" + android.defaultConfig.targetSdk + "/" + android.defaultConfig.targetSdkPreview,
                )
            }
            """,
            settingsLvm = "catalog = false\nandroid { compileSdk = 35; minSdk = 26; targetSdk = 34 }",
        )

        val line = fixture.build(":app:help").output.lines().single { it.startsWith("lvm-sdk ") }

        assertContains(line, "compileSdk=null/LvmPreview")
        assertContains(line, "/LvmPreview targetSdk=")
        assertContains(line, "targetSdk=")
        assertFalse(line.contains("=35/") || line.contains("=26/") || line.contains("=34/"), line)
    }

    @Test
    fun `release keeps proguard-rules pro of the module, and minify can be turned off`() {
        val fixture = applicationFixture(dir, "lvm { application { proguardFiles.add(\"extra-rules.pro\") } }\n" + PRINT_DSL)
        fixture.file("app/proguard-rules.pro", "-dontobfuscate")
        fixture.file("app/extra-rules.pro", "-dontoptimize")

        val minified = fixture.build(":app:help").output
        val notMinified = fixture.build(":app:help", "-Plvm.application.minify=false").output

        assertContains(minified, "minify=true shrink=true proguard=[$DEFAULT_KEEP_RULES, proguard-rules.pro, extra-rules.pro]")
        assertContains(notMinified, "minify=false shrink=false proguard=[$DEFAULT_KEEP_RULES, proguard-rules.pro, extra-rules.pro]")
    }

    @Test
    fun `an empty list of keep rules in settings or a Gradle property leaves out proguard-rules pro`() {
        val fixture = applicationFixture(dir, PRINT_DSL, settingsLvm = "catalog = false\napplication { proguardFiles = emptyList() }")
        fixture.file("app/proguard-rules.pro", "-dontobfuscate")
        val withoutRules = "proguard=[$DEFAULT_KEEP_RULES] animationsDisabled="

        assertContains(fixture.build(":app:help").output, withoutRules)

        fixture.file("settings.gradle.kts", GradleFixture.settingsHeader() + "\nrootProject.name = \"fixture\"\ninclude(\":app\")")
        assertContains(fixture.build(":app:help", "-Plvm.application.proguardFiles=").output, withoutRules)
    }

    @Test
    fun `desugaring adds desugar_jdk_libs unless turned off`() {
        val fixture = applicationFixture(dir, PRINT_DSL)

        val on = fixture.build(":app:dependencies", "--configuration", "coreLibraryDesugaring").output
        val off = fixture.build(":app:dependencies", "--configuration", "coreLibraryDesugaring", "-Plvm.application.desugaring=false").output

        assertContains(on, "desugaring=true")
        assertContains(on, "com.android.tools:desugar_jdk_libs:2.1.5")
        assertContains(off, "desugaring=false")
        assertFalse(off.contains("com.android.tools:desugar_jdk_libs"))
    }

    @Test
    fun `the Java version follows lvm values and native compileOptions, and Kotlin follows it`() {
        val fixture = applicationFixture(dir, "")

        fixture.build(":app:compileDebugKotlin")
        assertEquals(65, classFileMajorVersion(dir.debugClass("Hello")))

        fixture.build(":app:compileDebugKotlin", "-Plvm.jvm.target=17")
        assertEquals(61, classFileMajorVersion(dir.debugClass("Hello")))

        fixture.file("app/build.gradle.kts", "plugins { id(\"tech.annexflow.lvm.android.application\") }\ngroup = \"com.example\"\nlvm { jvm { target = 17 } }")
        fixture.build(":app:compileDebugKotlin")
        assertEquals(61, classFileMajorVersion(dir.debugClass("Hello")))

        fixture.file(
            "app/build.gradle.kts",
            """
            plugins { id("tech.annexflow.lvm.android.application") }
            group = "com.example"
            android {
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            """,
        )
        fixture.build(":app:compileDebugKotlin")
        assertEquals(61, classFileMajorVersion(dir.debugClass("Hello")))
    }

    @Test
    fun `passes lvm kotlin options to the compiler without the excluded opt-ins, and adds kotlin-test to unit tests`() {
        val fixture = applicationFixture(
            dir,
            """
            lvm {
                kotlin {
                    optIns.add("kotlin.ExperimentalStdlibApi")
                    excludedOptIns.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
                    freeCompilerArgs.add("-Xsuppress-version-warnings")
                }
            }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                val options = compilerOptions
                val taskName = name
                doFirst {
                    println(
                        "lvm-kotlin " + taskName + " warningsAsErrors=" + options.allWarningsAsErrors.get() +
                            " optIn=" + options.optIn.get() + " args=" + options.freeCompilerArgs.get(),
                    )
                }
            }
            """,
            settingsLvm = "catalog = false\nkotlin { warningsAsErrors = true; optIns.add(\"kotlinx.coroutines.ExperimentalCoroutinesApi\") }",
        )

        val compile = fixture.build(":app:compileDebugKotlin").output
        val classpath = fixture.build(":app:dependencies", "--configuration", "debugUnitTestRuntimeClasspath").output

        // AGP adds arguments of its own after lvm's.
        assertContains(compile, "lvm-kotlin compileDebugKotlin warningsAsErrors=true optIn=[kotlin.ExperimentalStdlibApi] args=[-Xsuppress-version-warnings")
        assertContains(classpath, "org.jetbrains.kotlin:kotlin-test-junit:")
    }
}
