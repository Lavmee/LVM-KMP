package tech.annexflow.lvm.blueprint.jvm

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class JvmSourceLayoutTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `the flat layout compiles Kotlin and Java from src and tests from test, and keeps resources in src main resources`() {
        val fixture = jvmFixture(dir, settingsLvm = FLAT_WITHOUT_CHECK)
        dir.resolve("lib/src/main/kotlin").deleteRecursively()
        fixture.file(
            "lib/src/Hello.kt",
            "class Hello { fun greet() = Greeter.greet() + \" \" + javaClass.getResource(\"/name.txt\")!!.readText().trim() }",
        )
        // Kotlin sees the Java file either way; only javac puts its class on the runtime classpath of the test.
        fixture.file("lib/src/Greeter.java", "public class Greeter { public static String greet() { return \"hello\"; } }")
        fixture.file("lib/src/main/resources/name.txt", "world")
        fixture.file(
            "lib/test/HelloTest.kt",
            "class HelloTest { @kotlin.test.Test fun greets() { kotlin.test.assertEquals(\"hello world\", Hello().greet()) } }",
        )

        fixture.build(":lib:test")

        assertTrue(dir.resolve("lib/build/test-results/test/TEST-HelloTest.xml").isFile)
    }

    @Test
    fun `the flat layout fails while conventional source directories remain`() {
        val fixture = jvmFixture(dir, settingsLvm = "catalog = false\nkotlin { flatLayout = true }")

        val output = fixture.buildAndFail(":lib:compileKotlin").output

        assertContains(output, "src/main/kotlin")
    }

    private companion object {
        /** The flat layout without lvmCheckSourceLayout, which rejects the fixture's files: they declare no package. */
        const val FLAT_WITHOUT_CHECK = "catalog = false\nkotlin {\n    flatLayout = true\n    checkSourceLayout = false\n}"
    }
}
