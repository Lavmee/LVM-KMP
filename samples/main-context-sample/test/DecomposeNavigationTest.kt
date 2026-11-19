package tech.annexflow.lvm.samples.maincontext

import com.arkivanov.decompose.DelicateDecomposeApi
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
import kotlinx.coroutines.IO
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import tech.annexflow.lvm.decompose.DecomposeNavigationHandler
import tech.annexflow.lvm.decompose.activate
import tech.annexflow.lvm.decompose.bringToFront
import tech.annexflow.lvm.decompose.dismiss
import tech.annexflow.lvm.decompose.navigate
import tech.annexflow.lvm.decompose.pop
import tech.annexflow.lvm.decompose.popTo
import tech.annexflow.lvm.decompose.popToFirst
import tech.annexflow.lvm.decompose.popWhile
import tech.annexflow.lvm.decompose.push
import tech.annexflow.lvm.decompose.pushNew
import tech.annexflow.lvm.decompose.pushNewWithPop
import tech.annexflow.lvm.decompose.pushToFront
import tech.annexflow.lvm.decompose.replaceAll
import tech.annexflow.lvm.decompose.replaceCurrent

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

    @OptIn(DelicateDecomposeApi::class)
    @Test
    fun `stack navigation called from a coroutine runs on the main dispatcher`() = runTest {
        val navigatedOn = mutableListOf<Any>()
        val stack = StackNavigation<String>()
        stack.subscribe { navigatedOn += currentThread() }

        withContext(Dispatchers.IO) {
            stack.push("list")
            stack.pushNew("details")
            stack.pushNewWithPop("item")
            stack.pushToFront("list")
            stack.bringToFront("settings")
            stack.pop()
            stack.popWhile { it != "home" }
            stack.popWhile(predicate = { it != "home" }, onComplete = {})
            stack.popTo(0)
            stack.popToFirst()
            stack.replaceCurrent("home")
            stack.replaceAll("home")
            stack.navigate { listOf("home") }
        }

        assertEquals(List(13) { mainThread }, navigatedOn)
    }

    @Test
    fun `slot navigation called from a coroutine runs on the main dispatcher`() = runTest {
        val navigatedOn = mutableListOf<Any>()
        val dialog = SlotNavigation<Unit>()
        val details = SlotNavigation<String>()
        dialog.subscribe { navigatedOn += currentThread() }
        details.subscribe { navigatedOn += currentThread() }

        withContext(Dispatchers.IO) {
            dialog.activate()
            details.activate("details")
            details.dismiss()
            details.navigate { "details" }
        }

        assertEquals(List(4) { mainThread }, navigatedOn)
    }
}
