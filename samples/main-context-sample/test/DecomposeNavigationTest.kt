package tech.annexflow.lvm.samples.maincontext

import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.stack.StackNavigation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import tech.annexflow.lvm.decompose.DecomposeNavigationHandler
import tech.annexflow.lvm.decompose.activate
import tech.annexflow.lvm.decompose.replaceAll

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class DecomposeNavigationTest {

    private lateinit var main: CloseableCoroutineDispatcher
    private lateinit var mainThread: Any

    @BeforeTest
    fun setMain() = runTest {
        main = newSingleThreadContext("lvm-main")
        Dispatchers.setMain(main)
        mainThread = withContext(main) { currentThread() }
    }

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
        main.close()
    }

    @Test
    fun `a navigation handler called from a coroutine runs on the main dispatcher`() = runTest {
        val handledOn = mutableListOf<Any>()
        val handler = DecomposeNavigationHandler<String> { handledOn += currentThread() }

        withContext(Dispatchers.Default) { handler("details") }

        assertEquals(listOf(mainThread), handledOn)
    }

    @Test
    fun `replaceAll and activate called from a coroutine run on the main dispatcher`() = runTest {
        val navigatedOn = mutableListOf<Any>()
        val stack = StackNavigation<String>()
        val slot = SlotNavigation<Unit>()
        stack.subscribe { navigatedOn += currentThread() }
        slot.subscribe { navigatedOn += currentThread() }

        withContext(Dispatchers.Default) {
            stack.replaceAll("home")
            slot.activate()
        }

        assertEquals(listOf(mainThread, mainThread), navigatedOn)
    }
}
