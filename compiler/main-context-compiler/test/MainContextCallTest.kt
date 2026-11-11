package tech.annexflow.lvm.maincontext.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher

class MainContextCallTest {

    @Test
    fun `calls from a suspend function and a suspend lambda run on the main dispatcher`() {
        val result = compileAndRun(
            """
            import tech.annexflow.lvm.maincontext.MainContext

            @MainContext
            fun navigate(): String = thread

            suspend fun load(): String = navigate()

            fun box(): String = withMainThread { listOf(load(), navigate()).joinToString() }
            """,
        )

        assertEquals("lvm-main, lvm-main", result)
    }

    @Test
    fun `the receivers and the arguments are evaluated once, in order, on the main dispatcher`() {
        val result = compileAndRun(
            """
            import tech.annexflow.lvm.maincontext.MainContext

            val events = mutableListOf<String>()

            class Screen

            class Host {
                @MainContext
                fun <T> Screen.show(first: String, second: T, vararg rest: Int): T {
                    events += "show@" + thread + rest.joinToString(prefix = "(", postfix = ")")
                    return second
                }
            }

            fun screen(): Screen {
                events += "screen@" + thread
                return Screen()
            }

            fun argument(name: String): String {
                events += name + "@" + thread
                return name
            }

            fun box(): String = withMainThread {
                val result = with(Host()) { screen().show(argument("first"), argument("second"), 1, 2) }
                (events + result).joinToString()
            }
            """,
        )

        assertEquals("screen@lvm-main, first@lvm-main, second@lvm-main, show@lvm-main(1, 2), second", result)
    }

    @Test
    fun `calls in lambdas inlined into a suspend function run on the main dispatcher`() {
        val result = compileAndRun(
            """
            import tech.annexflow.lvm.maincontext.MainContext

            @MainContext
            fun navigate(): String = thread

            suspend fun nested(): String = runCatching {
                listOf(1).map { listOf(2).map { navigate() }.single() }.single()
            }.getOrThrow()

            suspend fun folded(): String = runCatching { }.fold(onFailure = { throw it }, onSuccess = { navigate() })

            fun box(): String = withMainThread { listOf(nested(), folded()).joinToString() }
            """,
        )

        assertEquals("lvm-main, lvm-main", result)
    }

    @Test
    fun `calls from code that can't suspend run where they are made`() {
        val result = compileAndRun(
            """
            import tech.annexflow.lvm.maincontext.MainContext

            @MainContext
            fun navigate(): String = thread

            inline fun crossinlined(crossinline block: () -> String): String = block()

            inline fun notInlined(noinline block: () -> String): String = block()

            inline fun <T> passed(value: T): T = value

            fun interface Action {
                fun run(): String
            }

            inline fun passedAction(action: Action): Action = action

            suspend fun suspending(block: suspend () -> String): String = block()

            // Kotlin computes default values in a stub that can't suspend.
            suspend fun withDefault(value: String = navigate()): String = value

            suspend fun calls(): List<String> {
                fun local(): String = navigate()
                val reference = ::navigate
                return listOf(
                    local(),
                    crossinlined { navigate() },
                    notInlined { navigate() },
                    passed { navigate() }(),
                    passedAction { navigate() }.run(),
                    reference(),
                    suspending(::navigate),
                    withDefault(),
                )
            }

            fun box(): String = (listOf(navigate()) + withMainThread { calls() }).joinToString()
            """,
        )

        val threads = result.split(", ")
        assertEquals(9, threads.size, result)
        assertEquals(emptyList(), threads.filter { it == "lvm-main" }, result)
    }

    @Test
    fun `an annotated inline function runs together with its crossinline lambda on the main dispatcher`() {
        val result = compileAndRun(
            """
            import tech.annexflow.lvm.maincontext.MainContext

            val events = mutableListOf<String>()

            @MainContext
            inline fun activate(crossinline onComplete: () -> Unit) {
                events += "activate@" + thread
                onComplete()
            }

            fun box(): String = withMainThread {
                activate { events += "complete@" + thread }
                events.joinToString()
            }
            """,
        )

        assertEquals("activate@lvm-main, complete@lvm-main", result)
    }

    @Test
    fun `overrides inherit the annotation, also from a compiled library`() {
        val library = compile(
            """
            package library

            import tech.annexflow.lvm.maincontext.MainContext

            interface Navigation {
                @MainContext
                fun navigate(): String
            }

            open class BaseNavigation : Navigation {
                // The prelude's `thread` is in the root package, which a named package can't import.
                override fun navigate(): String = Thread.currentThread().name.substringBefore(" @")
            }
            """,
            plugin = false,
        )
        assertEquals(KotlinCompilation.ExitCode.OK, library.exitCode, library.messages)

        val result = compileAndRun(
            """
            import library.BaseNavigation

            class AppNavigation : BaseNavigation() {
                override fun navigate(): String = "app@" + super.navigate()
            }

            fun box(): String = withMainThread {
                listOf(BaseNavigation().navigate(), AppNavigation().navigate()).joinToString()
            }
            """,
            classpaths = listOf(library.outputDirectory),
        )

        assertEquals("lvm-main, app@lvm-main", result)
    }

    @Test
    fun `results, exceptions and cancellation reach the caller`() {
        val result = compileAndRun(
            """
            import kotlinx.coroutines.TimeoutCancellationException
            import kotlinx.coroutines.awaitCancellation
            import kotlinx.coroutines.delay
            import kotlinx.coroutines.withTimeout
            import tech.annexflow.lvm.maincontext.MainContext

            class Failure(val code: Int) : Exception()

            @MainContext
            suspend fun delayed(): Int {
                delay(10)
                return 42
            }

            @MainContext
            fun failing(): Nothing = throw Failure(code = 7)

            @MainContext
            suspend fun waiting(): Nothing = awaitCancellation()

            fun box(): String = withMainThread {
                val value = delayed()
                val caught = (runCatching { failing() }.exceptionOrNull() as Failure).code
                val cancelled = runCatching { withTimeout(50) { waiting() } }.exceptionOrNull()
                listOf(value, caught, cancelled is TimeoutCancellationException).joinToString()
            }
            """,
        )

        assertEquals("42, 7, true", result)
    }

    @Test
    fun `a call that jumps out of its arguments fails at the call`() {
        val source = """
            import tech.annexflow.lvm.maincontext.MainContext

            @MainContext
            fun navigate(screen: String) {}

            suspend fun open(screen: String?) {
                navigate(screen ?: return)
            }

            suspend fun openAll(screens: List<String?>) {
                for (screen in screens) {
                    navigate(screen ?: break)
                    navigate(screen ?: continue)
                }
            }
            """

        val result = compile(source)

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        for (call in listOf("?: return", "?: break", "?: continue")) {
            assertContains(result.messages, "Test.kt:${lineOf(source, call)}:")
        }
    }

    @Test
    fun `a call fails when onMainContext is not on the classpath`() {
        val source = """
            package tech.annexflow.lvm.maincontext

            annotation class MainContext

            @MainContext
            fun navigate() {}

            suspend fun open() {
                navigate()
            }
            """
        // Only kotlinx-coroutines, which the prelude needs: neither lvm-main-context nor its onMainContext.
        val coroutines = listOf(Dispatchers::class.java, TestDispatcher::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()) }

        val result = compile(source, classpaths = coroutines, inheritClassPath = false)

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertContains(result.messages, "Test.kt:${lineOf(source, "suspend fun open()") + 1}:")
    }
}
