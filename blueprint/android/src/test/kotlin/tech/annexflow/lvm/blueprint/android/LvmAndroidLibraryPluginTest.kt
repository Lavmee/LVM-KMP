package tech.annexflow.lvm.blueprint.android

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class LvmAndroidLibraryPluginTest {

    @TempDir
    lateinit var dir: File

    @BeforeTest
    fun requireAndroidSdk() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
    }

    @Test
    fun `uses default SDK levels, derives the namespace and the resource prefix and sets up tests and packaging`() {
        val output = libraryFixture(dir, PRINT_LIBRARY_DSL).build(":core:ui:help").output

        assertContains(
            output,
            "lvm-dsl namespace=com.example.core.ui compileSdk=37 minSdk=24 testTargetSdk=37 lintTargetSdk=37 " +
                "runner=androidx.test.runner.AndroidJUnitRunner resourcePrefix=core_ui_",
        )
        assertContains(
            output,
            "lvm-dsl desugaring=true consumerProguardFiles=[] animationsDisabled=true " +
                "excludes=[/META-INF/DEPENDENCIES, /META-INF/INDEX.LIST]",
        )
        assertContains(output, "lvm-variant debug androidTest=false")
    }

    @Test
    fun `module values win over Gradle properties, which win over settings values`() {
        val fixture = libraryFixture(
            dir,
            """
            lvm {
                android { targetSdk = 36 }
                library {
                    desugaring = true
                    skipEmptyAndroidTest = true
                }
            }
            """ + PRINT_LIBRARY_DSL,
            settingsLvm = "catalog = false\n" +
                "android { minSdk = 26; targetSdk = 35; testInstrumentationRunner = \"com.example.SettingsRunner\" }\n" +
                "library { desugaring = false; resourcePrefix = false; resourcePrefixDroppedSegments = listOf(\"ui\") }",
        )

        val output = fixture.build(
            ":core:ui:help",
            "-Plvm.android.testInstrumentationRunner=com.example.PropertyRunner",
            "-Plvm.library.resourcePrefix=true",
            "-Plvm.library.skipEmptyAndroidTest=false",
        ).output

        assertContains(output, "minSdk=26 testTargetSdk=36 lintTargetSdk=36 runner=com.example.PropertyRunner resourcePrefix=core_")
        assertContains(output, "lvm-dsl desugaring=true")
        assertContains(output, "lvm-variant debug androidTest=false")
    }

    @Test
    fun `native DSL values win`() {
        val fixture = libraryFixture(
            dir,
            """
            android {
                namespace = "com.example.nativedsl"
                compileSdk = 36
                defaultConfig {
                    minSdk = 30
                    testInstrumentationRunner = "com.example.NativeRunner"
                    consumerProguardFiles("native-rules.pro")
                }
                testOptions.targetSdk = 34
                testOptions.animationsDisabled = false
                lint.targetSdk = 35
                resourcePrefix = "native_"
                compileOptions.isCoreLibraryDesugaringEnabled = true
            }
            lvm {
                android { minSdk = 26; targetSdk = 33; testInstrumentationRunner = "com.example.LvmRunner" }
                library { desugaring = false }
            }
            """ + PRINT_LIBRARY_DSL,
        )
        fixture.file("core/ui/consumer-rules.pro", "-keep class Hello")

        val output = fixture.build(":core:ui:help").output

        assertContains(
            output,
            "lvm-dsl namespace=com.example.nativedsl compileSdk=36 minSdk=30 testTargetSdk=34 lintTargetSdk=35 " +
                "runner=com.example.NativeRunner resourcePrefix=native_",
        )
        assertContains(output, "lvm-dsl desugaring=true consumerProguardFiles=[native-rules.pro] animationsDisabled=false")
    }

    @Test
    fun `native preview SDK levels are left alone`() {
        val fixture = libraryFixture(
            dir,
            """
            android {
                compileSdkPreview = "LvmPreview"
                defaultConfig { minSdk { version = preview("LvmPreview") } }
                testOptions.targetSdkPreview = "LvmPreview"
                lint.targetSdkPreview = "LvmPreview"
            }
            androidComponents.finalizeDsl { android ->
                println(
                    "lvm-sdk compileSdk=" + android.compileSdk + "/" + android.compileSdkPreview +
                        " minSdk=" + android.defaultConfig.minSdk +
                        " testTargetSdk=" + android.testOptions.targetSdk + "/" + android.testOptions.targetSdkPreview +
                        " lintTargetSdk=" + android.lint.targetSdk + "/" + android.lint.targetSdkPreview,
                )
            }
            """,
            settingsLvm = "catalog = false\nandroid { compileSdk = 35; minSdk = 26; targetSdk = 34 }",
        )

        val line = fixture.build(":core:ui:help").output.lines().single { it.startsWith("lvm-sdk ") }

        assertContains(line, "compileSdk=null/LvmPreview")
        assertContains(line, "/LvmPreview lintTargetSdk=")
        assertTrue(line.endsWith("/LvmPreview"), line)
        assertFalse(line.contains("=35/") || line.contains("minSdk=26") || line.contains("=34/"), line)
    }

    @Test
    fun `consumer keep rules come from consumer-rules pro, and an empty list in settings leaves it out`() {
        val fixture = libraryFixture(
            dir,
            "lvm { library { consumerProguardFiles.addAll(\"extra-rules.pro\", \"missing-rules.pro\") } }\n" + PRINT_LIBRARY_DSL,
        )
        fixture.file("core/ui/consumer-rules.pro", "-keep class Hello")
        fixture.file("core/ui/extra-rules.pro", "-keepnames class Hello")

        val withDefault = fixture.build(":core:ui:help").output
        fixture.file("settings.gradle.kts", librarySettings("catalog = false\nlibrary { consumerProguardFiles = emptyList() }"))
        val withEmptySettingsList = fixture.build(":core:ui:help").output

        assertContains(withDefault, "consumerProguardFiles=[consumer-rules.pro, extra-rules.pro]")
        assertContains(withEmptySettingsList, "consumerProguardFiles=[extra-rules.pro]")
    }

    @Test
    fun `desugaring adds desugar_jdk_libs unless turned off`() {
        val fixture = libraryFixture(dir, PRINT_LIBRARY_DSL)

        val on = fixture.build(":core:ui:dependencies", "--configuration", "coreLibraryDesugaring").output
        val off = fixture.build(
            ":core:ui:dependencies", "--configuration", "coreLibraryDesugaring", "-Plvm.library.desugaring=false",
        ).output

        assertContains(on, "desugaring=true")
        assertContains(on, "com.android.tools:desugar_jdk_libs:2.1.5")
        assertContains(off, "desugaring=false")
        assertFalse(off.contains("com.android.tools:desugar_jdk_libs"))
    }

    @Test
    fun `the Java version follows lvm values and native compileOptions, and Kotlin follows it`() {
        val fixture = libraryFixture(dir, "")
        val header = "plugins { id(\"tech.annexflow.lvm.android.library\") }\ngroup = \"com.example\"\n"

        fixture.build(":core:ui:compileDebugKotlin")
        assertEquals(65, classFileMajorVersion(dir.libraryClass("Hello")))

        fixture.build(":core:ui:compileDebugKotlin", "-Plvm.jvm.target=17")
        assertEquals(61, classFileMajorVersion(dir.libraryClass("Hello")))

        fixture.file("core/ui/build.gradle.kts", header + "lvm { jvm { target = 17 } }")
        fixture.build(":core:ui:compileDebugKotlin")
        assertEquals(61, classFileMajorVersion(dir.libraryClass("Hello")))

        fixture.file(
            "core/ui/build.gradle.kts",
            header + "android.compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }",
        )
        fixture.build(":core:ui:compileDebugKotlin")
        assertEquals(61, classFileMajorVersion(dir.libraryClass("Hello")))
    }

    @Test
    fun `passes lvm kotlin options to the compiler without the excluded opt-ins, and adds kotlin-test to unit tests`() {
        val fixture = libraryFixture(
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

        val compile = fixture.build(":core:ui:compileDebugKotlin").output
        val classpath = fixture.build(":core:ui:dependencies", "--configuration", "debugUnitTestRuntimeClasspath").output

        // AGP adds arguments of its own after lvm's.
        assertContains(compile, "lvm-kotlin compileDebugKotlin warningsAsErrors=true optIn=[kotlin.ExperimentalStdlibApi] args=[-Xsuppress-version-warnings")
        assertContains(classpath, "org.jetbrains.kotlin:kotlin-test-junit:")
    }

    @Test
    fun `jvmDefault from settings reaches the compiler, and Kotlin's own default stays while it is unset`() {
        val fixture = libraryFixture(dir, "")
        fixture.file("core/ui/src/main/kotlin/Greeter.kt", "interface Greeter { fun greet(): String = \"hello\" }")
        val defaultImpls = dir.libraryClass("Greeter\$DefaultImpls")

        fixture.build(":core:ui:compileDebugKotlin")
        assertTrue(defaultImpls.isFile)

        fixture.file("settings.gradle.kts", librarySettings("catalog = false\nkotlin { jvmDefault = \"no-compatibility\" }"))
        fixture.build(":core:ui:compileDebugKotlin")
        assertFalse(defaultImpls.exists())
    }

    @Test
    fun `explicit API mode covers main code and leaves tests alone`() {
        val fixture = libraryFixture(dir, "lvm { kotlin { explicitApi = true } }")
        fixture.file("core/ui/src/test/kotlin/HelloTest.kt", "class HelloTest { @kotlin.test.Test fun works() { Hello() } }")

        val implicitVisibility = fixture.buildAndFail(":core:ui:compileDebugKotlin")
        fixture.file("core/ui/src/main/kotlin/Hello.kt", "public class Hello")
        fixture.build(":core:ui:testDebugUnitTest")

        assertEquals(TaskOutcome.FAILED, implicitVisibility.task(":core:ui:compileDebugKotlin")?.outcome)
        assertTrue(dir.resolve("core/ui/build/test-results/testDebugUnitTest/TEST-HelloTest.xml").isFile)
    }

    @Test
    fun `instrumented tests are off without androidTest sources, and on with them or when the switch is off`() {
        val fixture = libraryFixture(dir, PRINT_LIBRARY_DSL, settingsLvm = "catalog = false\nkotlin { flatLayout = true }")
        dir.resolve("core/ui/src/main/kotlin").deleteRecursively()
        fixture.file("core/ui/src/Hello.kt", "class Hello")
        val off = "lvm-variant debug androidTest=false"
        val on = "lvm-variant debug androidTest=true"

        assertContains(fixture.build(":core:ui:help").output, off)
        assertContains(fixture.build(":core:ui:help", "-Plvm.library.skipEmptyAndroidTest=false").output, on)

        fixture.file("core/ui/androidTest@debug/HelloTest.kt", "class HelloTest")
        assertContains(fixture.build(":core:ui:help").output, on)

        dir.resolve("core/ui/androidTest@debug").deleteRecursively()
        fixture.file("core/ui/src/androidTest/AndroidManifest.xml", "<manifest />")
        assertContains(fixture.build(":core:ui:help").output, on)
    }
}
