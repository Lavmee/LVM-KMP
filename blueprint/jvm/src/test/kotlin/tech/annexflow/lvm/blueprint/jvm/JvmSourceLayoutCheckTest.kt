package tech.annexflow.lvm.blueprint.jvm

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class JvmSourceLayoutCheckTest {

    @TempDir
    lateinit var dir: File

    /** A flat-layout module `:lib` whose only sources are [files], by path in the module. */
    private fun flatModule(
        vararg files: Pair<String, String>,
        body: String = "",
        settingsLvm: String = "catalog = false\nkotlin { flatLayout = true }",
    ): GradleFixture = jvmFixture(dir, body, settingsLvm).apply {
        dir.resolve("lib/src/main/kotlin").deleteRecursively()
        for ((path, source) in files) file("lib/$path", source)
    }

    @Test
    fun `files in the directories of their packages compile`() {
        val fixture = flatModule(
            HELLO,
            "src/ui/Screen.kt" to "@file:JvmName(\"Screens\")\n\npackage com.example.lib.ui\n\nfun screen() = com.example.lib.Hello()",
            "src/data/Repository.java" to "package com.example.lib.data;\n\npublic class Repository {}",
            "test/ui/ScreenTest.kt" to "package com.example.lib.ui\n\nclass ScreenTest",
        )

        val result = fixture.build(":lib:compileTestKotlin")

        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:lvmCheckSourceLayout")?.outcome)
        assertTrue(dir.mainClass("com/example/lib/ui/Screens").isFile)
    }

    @Test
    fun `a file in the directory of another package stops the compilation, which names every such file and the packages`() {
        val fixture = flatModule(
            HELLO,
            "src/net/Client.kt" to "package com.example.lib.net\n\nclass Client",
            "src/ui/Model.kt" to "package com.example.lib.data\n\nclass Model",
            "test/data/RepositoryTest.java" to "package com.example.lib.repository;\n\nclass RepositoryTest {}",
        )

        val result = fixture.buildAndFail(":lib:compileKotlin")

        assertNull(result.task(":lib:compileKotlin"))
        assertContains(result.output, "src/ui/Model.kt")
        assertContains(result.output, "test/data/RepositoryTest.java")
        // What the directory of Model.kt expects, and a package that only the list of the module's packages names.
        assertContains(result.output, "com.example.lib.ui")
        assertContains(result.output, "com.example.lib.net")
    }

    @Test
    fun `directories that repeat the root package fail`() {
        val fixture = flatModule(HELLO, "src/com/example/lib/ui/Screen.kt" to "package com.example.lib.ui\n\nclass Screen")

        val output = fixture.buildAndFail(":lib:compileKotlin").output

        assertContains(output, "src/com/example/lib/ui/Screen.kt")
    }

    @Test
    fun `a file without a package declaration fails`() {
        val fixture = flatModule(HELLO, "src/Util.kt" to "/* package com.example.lib */\n\nfun util() = 1")

        val output = fixture.buildAndFail(":lib:compileKotlin").output

        assertContains(output, "src/Util.kt")
    }

    @Test
    fun `a module turns the check off in its lvm block`() {
        val fixture = flatModule(HELLO, MISPLACED, body = "lvm { kotlin { checkSourceLayout = false } }")

        fixture.build(":lib:compileKotlin")

        assertTrue(dir.mainClass("com/example/lib/data/Model").isFile)
    }

    @Test
    fun `settings turn the check off for the whole build`() {
        val fixture = flatModule(
            HELLO,
            MISPLACED,
            settingsLvm = "catalog = false\nkotlin {\n    flatLayout = true\n    checkSourceLayout = false\n}",
        )

        fixture.build(":lib:compileKotlin")

        assertTrue(dir.mainClass("com/example/lib/data/Model").isFile)
    }

    @Test
    fun `the check stays up to date until a file changes, and reads the files again with the configuration cache`() {
        val fixture = flatModule(HELLO, "src/ui/Screen.kt" to "package com.example.lib.ui\n\nclass Screen")
        fixture.build(":lib:lvmCheckSourceLayout")

        val again = fixture.build(":lib:lvmCheckSourceLayout")
        fixture.file("lib/src/ui/Screen.kt", "package com.example.lib.screen\n\nclass Screen")
        val changed = fixture.buildAndFail(":lib:lvmCheckSourceLayout")

        assertEquals(TaskOutcome.UP_TO_DATE, again.task(":lib:lvmCheckSourceLayout")?.outcome)
        assertContains(changed.output, "src/ui/Screen.kt")
    }

    private companion object {
        val HELLO = "src/Hello.kt" to "package com.example.lib\n\nclass Hello"

        /** In the directory of `ui`, but declaring `data`. */
        val MISPLACED = "src/ui/Model.kt" to "package com.example.lib.data\n\nclass Model"
    }
}
