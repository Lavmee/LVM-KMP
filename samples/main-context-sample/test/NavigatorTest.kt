package tech.annexflow.lvm.samples.maincontext

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class NavigatorTest {

    private lateinit var main: CloseableCoroutineDispatcher
    private lateinit var mainThread: Any
    private val pushedOn = mutableListOf<Any>()
    private val navigator = Navigator { pushedOn += currentThread() }

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
    fun `a push from main code runs on the main dispatcher`() = runTest {
        withContext(Dispatchers.Default) { navigator.pushLoaded { "details" } }

        assertEquals(listOf(mainThread), pushedOn)
    }

    @Test
    fun `a push from a test coroutine runs on the main dispatcher`() = runTest {
        withContext(Dispatchers.Default) { navigator.push("details") }

        assertEquals(listOf(mainThread), pushedOn)
    }

    @Test
    fun `an implementation of an annotated function runs on the main dispatcher`() = runTest {
        val openedOn = mutableListOf<Any>()
        val router = object : Router {
            override fun open(screen: String) {
                openedOn += currentThread()
            }
        }

        withContext(Dispatchers.Default) { router.open("details") }

        assertEquals(listOf(mainThread), openedOn)
    }

    @Test
    fun `a push from code that can't suspend runs on the calling thread`() {
        navigator.push("details")

        assertEquals(currentThread(), pushedOn.single())
        assertNotEquals(mainThread, pushedOn.single())
    }
}
