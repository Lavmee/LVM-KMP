package tech.annexflow.lvm.maincontext.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.io.File
import java.io.OutputStream
import kotlin.test.assertEquals

/**
 * Compiles [source] as `Test.kt`, next to [PRELUDE], against [classpaths] and, if [inheritClassPath] is set, the test
 * classpath. [plugin] turns the main-context compiler plugin on.
 */
fun compile(
    source: String,
    plugin: Boolean = true,
    classpaths: List<File> = emptyList(),
    inheritClassPath: Boolean = true,
): JvmCompilationResult =
    KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("Test.kt", source.trimIndent()), SourceFile.kotlin("Prelude.kt", PRELUDE))
        if (plugin) compilerPluginRegistrars = listOf(MainContextCompilerPluginRegistrar())
        this.inheritClassPath = inheritClassPath
        this.classpaths = classpaths
        jvmTarget = "17"
        messageOutputStream = OutputStream.nullOutputStream()
    }.compile()

/** Compiles [source], which declares a top-level `fun box(): String`, and returns what `box` returns. */
fun compileAndRun(source: String, classpaths: List<File> = emptyList()): String {
    val result = compile(source, classpaths = classpaths)
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    return result.classLoader.loadClass("TestKt").getMethod("box").invoke(null) as String
}

/** Line of the first line of [source] that contains [text], counted like compiler messages. */
fun lineOf(source: String, text: String): Int = source.trimIndent().lines().indexOfFirst { text in it } + 1

private val PRELUDE = """
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.DelicateCoroutinesApi
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.ExperimentalCoroutinesApi
    import kotlinx.coroutines.newSingleThreadContext
    import kotlinx.coroutines.runBlocking
    import kotlinx.coroutines.test.resetMain
    import kotlinx.coroutines.test.setMain

    /** Runs [block] on Dispatchers.Default while Dispatchers.Main runs on a thread named lvm-main. */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun <T> withMainThread(block: suspend CoroutineScope.() -> T): T = newSingleThreadContext("lvm-main").use { main ->
        Dispatchers.setMain(main)
        try {
            runBlocking(Dispatchers.Default, block)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** The name of the current thread, without the coroutine name that the coroutines debug mode appends. */
    val thread: String get() = Thread.currentThread().name.substringBefore(" @")
""".trimIndent()
