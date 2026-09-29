package tech.annexflow.lvm.blueprint.multiplatform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class IosTargetsTest {

    @TempDir
    lateinit var dir: File

    private val iosModule = """
        plugins { id("tech.annexflow.lvm.multiplatform") }
        lvm {
            targets {
                ios { framework { baseName = "FixtureKit"; isStatic = true } }
            }
        }
    """

    @Test
    fun `creates both architectures with frameworks by default`() {
        val fixture = kmpFixture(dir, iosModule)

        val output = fixture.build(":lib:tasks", "--all").output

        assertContains(output, "linkDebugFrameworkIosArm64")
        assertContains(output, "linkDebugFrameworkIosSimulatorArm64")
    }

    @Test
    fun `architectures come from the build-wide value`() {
        val fixture = kmpFixture(dir, iosModule)

        val output = fixture.build(":lib:tasks", "--all", "-Plvm.ios.architectures=iosSimulatorArm64").output

        assertContains(output, "linkDebugFrameworkIosSimulatorArm64")
        assertFalse(output.contains("linkDebugFrameworkIosArm64"))
    }

    @Test
    fun `a framework needs a base name`() {
        val fixture = kmpFixture(
            dir,
            """
            plugins { id("tech.annexflow.lvm.multiplatform") }
            lvm { targets { ios { framework { isStatic = true } } } }
            """,
        )

        val result = fixture.buildAndFail(":lib:help")

        assertContains(result.output, "baseName")
    }
}
