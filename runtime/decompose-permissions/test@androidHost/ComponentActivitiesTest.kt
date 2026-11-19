package tech.annexflow.lvm.decompose.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.arkivanov.decompose.childContext
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionDelegate
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import tech.annexflow.lvm.decompose.AppComponentContext
import tech.annexflow.lvm.logger.Logger

@RunWith(RobolectricTestRunner::class)
class ComponentActivitiesTest {

    @Test
    fun `the retained root keeps one controller that is bound to every new activity`() {
        val activity = Robolectric.buildActivity(RetainedRootActivity::class.java).setup()
        val root = activity.get().root

        repeat(2) {
            // The dialog is open while the activity is recreated, and the user answers it in the new activity.
            val request = root.permissionsController.requestCamera()
            val dialog = activity.get().shownCameraDialog()
            activity.recreate()
            activity.get().answer(dialog, PackageManager.PERMISSION_GRANTED)

            assertSame(root, activity.get().root)
            assertTrue(request.isCompleted, "The answer didn't reach the request")
            // Fails with the request's exception, such as DeniedException.
            runBlocking { request.await() }
        }
    }

    @Test
    fun `a child of the retained root reads the controller of the activity`() {
        val activity = Robolectric.buildActivity(RetainedRootActivity::class.java).setup()

        assertSame(activity.get().retainedPermissionsController(), activity.get().root.camera.permissionsController)
    }

    @Test
    fun `every default root gets the controller of the activity, bound to the current one`() {
        val activity = Robolectric.buildActivity(DefaultRootActivity::class.java).setup()
        val first = activity.get().root

        activity.recreate()
        val second = activity.get().root
        second.permissionsController.requestCamera()

        assertNotSame(first, second)
        assertSame(first.permissionsController, second.permissionsController)
        activity.get().shownCameraDialog()
    }
}

/** Creates its root with retainedAppComponentWithPermissions in every onCreate, as an app does. */
class RetainedRootActivity : ComponentActivity() {
    lateinit var root: RootComponent
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = retainedAppComponentWithPermissions(logger = SilentLogger, factory = ::RootComponent)
    }
}

/** Creates its root with defaultAppComponentWithPermissions in every onCreate, as an app does. */
class DefaultRootActivity : ComponentActivity() {
    lateinit var root: AppComponentContext
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = defaultAppComponentWithPermissions(logger = SilentLogger)
    }
}

class RootComponent(context: AppComponentContext) : AppComponentContext by context {
    val camera: CameraComponent = CameraComponent(childContext(key = "camera"))
}

class CameraComponent(context: AppComponentContext) : AppComponentContext by context, PermissionsControllerOwner {
    override val permissionsController: PermissionsController
        get() = locals[LocalPermissionsController]
}

private val SilentLogger = Logger { _, _, _, _ -> }

/** Requests the camera. The request runs until the user answers it. */
private fun PermissionsController.requestCamera(): Deferred<Unit> =
    // Unconfined, so the dialog is shown before this returns.
    CoroutineScope(Dispatchers.Unconfined).async { providePermission(CameraPermission) }

/** The camera dialog that this activity shows: the activity that requestPermissions started. */
private fun ComponentActivity.shownCameraDialog(): Intent {
    val request = assertNotNull(shadowOf(this).lastRequestedPermission, "The activity shows no permission dialog")
    assertContentEquals(arrayOf(Manifest.permission.CAMERA), request.requestedPermissions)
    return assertNotNull(shadowOf(this).nextStartedActivityForResult).intent
}

/**
 * Answers [dialog] with [result], as the system does when the user taps a button: the activity gets the result of
 * the dialog, clears its pending request and passes the answer to its ActivityResultRegistry. Calling
 * onRequestPermissionsResult instead would keep the request pending, and the activity would cancel the next one.
 */
private fun ComponentActivity.answer(dialog: Intent, result: Int) {
    // The names of the extras are hidden in the SDK.
    val answer = Intent()
        .putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES", arrayOf(Manifest.permission.CAMERA))
        .putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_RESULTS", intArrayOf(result))
    shadowOf(this).receiveResult(dialog, Activity.RESULT_OK, answer)
}

private object CameraPermission : Permission {
    override val delegate: PermissionDelegate = object : PermissionDelegate {
        override fun getPermissionStateOverride(applicationContext: Context): PermissionState? = null

        override fun getPlatformPermission(): List<String> = listOf(Manifest.permission.CAMERA)
    }
}
