package tech.annexflow.lvm.decompose.permissions

import com.arkivanov.decompose.childContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import tech.annexflow.lvm.decompose.PreviewAppComponentContext
import tech.annexflow.lvm.decompose.provideLocals
import tech.annexflow.lvm.decompose.provides

class LocalPermissionsControllerTest {

    @Test
    fun `components get the controller the root provided`() {
        val controller = StubPermissionsController()
        val root = PreviewAppComponentContext().provideLocals(LocalPermissionsController provides controller)

        assertSame(controller, root.childContext("child").permissionsController)
    }

    @Test
    fun `reading the controller fails when the root provided none`() {
        assertFailsWith<IllegalStateException> { PreviewAppComponentContext().permissionsController }
    }
}
