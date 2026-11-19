package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.SlotNavigator
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.stack.StackNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.arkivanov.decompose.router.slot.activate as decomposeActivate
import com.arkivanov.decompose.router.slot.dismiss as decomposeDismiss
import com.arkivanov.decompose.router.slot.navigate as decomposeSlotNavigate
import com.arkivanov.decompose.router.stack.bringToFront as decomposeBringToFront
import com.arkivanov.decompose.router.stack.navigate as decomposeStackNavigate
import com.arkivanov.decompose.router.stack.pop as decomposePop
import com.arkivanov.decompose.router.stack.popTo as decomposePopTo
import com.arkivanov.decompose.router.stack.popToFirst as decomposePopToFirst
import com.arkivanov.decompose.router.stack.popWhile as decomposePopWhile
import com.arkivanov.decompose.router.stack.push as decomposePush
import com.arkivanov.decompose.router.stack.pushNew as decomposePushNew
import com.arkivanov.decompose.router.stack.pushToFront as decomposePushToFront
import com.arkivanov.decompose.router.stack.replaceAll as decomposeReplaceAll
import com.arkivanov.decompose.router.stack.replaceCurrent as decomposeReplaceCurrent

class NavigatorsTest {

    @OptIn(DelicateDecomposeApi::class)
    @Test
    fun `push changes the stack as Decompose's does`() {
        forEachStackAndConfiguration { stack, configuration ->
            assertSameStack(
                stack,
                lvm = { done -> push(configuration) { done(Unit) } },
                decompose = { done -> decomposePush(configuration) { done(Unit) } },
            )
        }
    }

    @Test
    fun `pushNew changes the stack as Decompose's does`() {
        forEachStackAndConfiguration { stack, configuration ->
            assertSameStack(
                stack,
                lvm = { done -> pushNew(configuration, done) },
                decompose = { done -> decomposePushNew(configuration, done) },
            )
        }
    }

    @Test
    fun `pushToFront changes the stack as Decompose's does`() {
        forEachStackAndConfiguration { stack, configuration ->
            assertSameStack(
                stack,
                lvm = { done -> pushToFront(configuration) { done(Unit) } },
                decompose = { done -> decomposePushToFront(configuration) { done(Unit) } },
            )
        }
    }

    @Test
    fun `bringToFront changes the stack as Decompose's does`() {
        forEachStackAndConfiguration { stack, configuration ->
            assertSameStack(
                stack,
                lvm = { done -> bringToFront(configuration) { done(Unit) } },
                decompose = { done -> decomposeBringToFront(configuration) { done(Unit) } },
            )
        }
    }

    @Test
    fun `pop changes the stack as Decompose's does`() {
        for (stack in STACKS) {
            assertSameStack(stack, lvm = { done -> pop(done) }, decompose = { done -> decomposePop(done) })
        }
    }

    @Test
    fun `popWhile changes the stack as Decompose's does`() {
        val predicates = listOf<(Any) -> Boolean>({ it is String }, { it == "list" }, { true }, { false })
        for (stack in STACKS) {
            for (predicate in predicates) {
                assertSameStack(
                    stack,
                    lvm = { popWhile(predicate) },
                    decompose = { decomposePopWhile(predicate) },
                )
                assertSameStack(
                    stack,
                    lvm = { done -> popWhile(predicate, done) },
                    decompose = { done -> decomposePopWhile(predicate, done) },
                )
            }
        }
    }

    @Test
    fun `popTo changes the stack as Decompose's does`() {
        for (stack in STACKS) {
            for (index in 0..stack.size) {
                assertSameStack(
                    stack,
                    lvm = { done -> popTo(index, done) },
                    decompose = { done -> decomposePopTo(index, done) },
                )
            }
        }
    }

    @Test
    fun `popTo fails on a negative index as Decompose's does`() {
        assertFailsWith<IllegalArgumentException> { FakeStackNavigator(listOf("home")).popTo(-1) }
    }

    @Test
    fun `popToFirst changes the stack as Decompose's does`() {
        for (stack in STACKS) {
            assertSameStack(
                stack,
                lvm = { done -> popToFirst(done) },
                decompose = { done -> decomposePopToFirst(done) },
            )
        }
    }

    @Test
    fun `replaceCurrent changes the stack as Decompose's does`() {
        forEachStackAndConfiguration { stack, configuration ->
            assertSameStack(
                stack,
                lvm = { done -> replaceCurrent(configuration) { done(Unit) } },
                decompose = { done -> decomposeReplaceCurrent(configuration) { done(Unit) } },
            )
        }
    }

    @Test
    fun `replaceAll changes the stack as Decompose's does`() {
        for (stack in STACKS) {
            for (configurations in listOf(listOf("home"), listOf("list", 1), stack.reversed())) {
                assertSameStack(
                    stack,
                    lvm = { done -> replaceAll(*configurations.toTypedArray()) { done(Unit) } },
                    decompose = { done -> decomposeReplaceAll(*configurations.toTypedArray()) { done(Unit) } },
                )
            }
        }
    }

    @Test
    fun `navigate changes the stack as Decompose's does`() {
        val transformers = listOf<(List<Any>) -> List<Any>>({ it + "details" }, { it.take(1) }, { it.reversed() })
        for (stack in STACKS) {
            for (transformer in transformers) {
                assertSameStack(
                    stack,
                    lvm = { navigate(transformer) },
                    decompose = { decomposeStackNavigate(transformer) },
                )
            }
        }
    }

    @Test
    fun `pushNewWithPop replaces the top n configurations`() {
        val stack = listOf("home", "list", "item")

        assertEquals(
            listOf("home", "list", "details") to listOf(true),
            stackAfter(stack) { done -> pushNewWithPop("details", onComplete = done) },
        )
        assertEquals(
            listOf("home", "details") to listOf(true),
            stackAfter(stack) { done -> pushNewWithPop("details", n = 2, onComplete = done) },
        )
    }

    @Test
    fun `pushNewWithPop leaves the stack as it is when the configuration is on top`() {
        val stack = listOf("home", "list", "details")

        assertEquals(stack to listOf(false), stackAfter(stack) { done -> pushNewWithPop("details", 2, done) })
    }

    @Test
    fun `pushNewWithPop leaves only the configuration when n covers the whole stack`() {
        val stack = listOf("home", "list")
        val onlyDetails = listOf("details") to listOf(true)

        assertEquals(onlyDetails, stackAfter(stack) { done -> pushNewWithPop("details", 2, done) })
        assertEquals(onlyDetails, stackAfter(stack) { done -> pushNewWithPop("details", 5, done) })
    }

    @Test
    fun `pushNewWithPop with n 0 changes the stack as Decompose's pushNew does`() {
        forEachStackAndConfiguration { stack, configuration ->
            assertSameStack(
                stack,
                lvm = { done -> pushNewWithPop(configuration, n = 0, onComplete = done) },
                decompose = { done -> decomposePushNew(configuration, done) },
            )
        }
    }

    @Test
    fun `pushNewWithPop fails on a negative n`() {
        assertFailsWith<IllegalArgumentException> { FakeStackNavigator(listOf("home")).pushNewWithPop("details", -1) }
    }

    @Test
    fun `activate with a configuration changes the slot as Decompose's does`() {
        for (active in listOf(null, "dialog", "sheet")) {
            assertSameSlot(
                active,
                lvm = { done -> activate("dialog") { done(Unit) } },
                decompose = { done -> decomposeActivate("dialog") { done(Unit) } },
            )
        }
    }

    @Test
    fun `dismiss changes the slot as Decompose's does`() {
        for (active in listOf(null, "dialog")) {
            assertSameSlot(active, lvm = { done -> dismiss(done) }, decompose = { done -> decomposeDismiss(done) })
        }
    }

    @Test
    fun `navigate changes the slot as Decompose's does`() {
        val transformers = listOf<(Any?) -> Any?>({ "sheet" }, { null }, { it })
        for (active in listOf(null, "dialog")) {
            for (transformer in transformers) {
                assertSameSlot(
                    active,
                    lvm = { navigate(transformer) },
                    decompose = { decomposeSlotNavigate(transformer) },
                )
            }
        }
    }

    @Test
    fun `activate opens a slot without a configuration and completes`() {
        val navigation = SlotNavigation<Unit>()
        val slot = rootContext().childSlot(
            source = navigation,
            serializer = null,
            childFactory = { _, _ -> "dialog" },
        )
        var completed = false

        navigation.activate { completed = true }

        assertNotNull(slot.value.child)
        assertTrue(completed)
    }
}

/** Stacks of one configuration, of two, of two classes, and with a duplicate. */
private val STACKS: List<List<Any>> = listOf(
    listOf("home"),
    listOf("home", "list"),
    listOf("home", 1, "list", 2),
    listOf("home", "list", "home"),
)

/**
 * Runs [block] for every stack of [STACKS] with each of its configurations, one of the same class that it lacks and one
 * of another class.
 */
private inline fun forEachStackAndConfiguration(block: (stack: List<Any>, configuration: Any) -> Unit) {
    for (stack in STACKS) {
        for (configuration in (stack + listOf("details", 3)).distinct()) block(stack, configuration)
    }
}

/** Asserts that [lvm] and [decompose] make the same stack from [stack] and complete with the same values. */
private fun assertSameStack(
    stack: List<Any>,
    lvm: StackNavigator<Any>.(done: (Any) -> Unit) -> Unit,
    decompose: StackNavigator<Any>.(done: (Any) -> Unit) -> Unit,
) {
    assertEquals(stackAfter(stack, decompose), stackAfter(stack, lvm), "From $stack")
}

/** The stack that [navigation] makes from [stack], and every value it completes with. */
private fun stackAfter(
    stack: List<Any>,
    navigation: StackNavigator<Any>.(done: (Any) -> Unit) -> Unit,
): Pair<List<Any>, List<Any>> {
    val navigator = FakeStackNavigator(stack)
    val completions = mutableListOf<Any>()
    navigator.navigation { completions += it }
    return navigator.stack to completions
}

/** Asserts that [lvm] and [decompose] make the same slot from [active] and complete with the same values. */
private fun assertSameSlot(
    active: Any?,
    lvm: SlotNavigator<Any>.(done: (Any) -> Unit) -> Unit,
    decompose: SlotNavigator<Any>.(done: (Any) -> Unit) -> Unit,
) {
    assertEquals(slotAfter(active, decompose), slotAfter(active, lvm), "From $active")
}

/** The configuration that [navigation] activates from [active], and every value it completes with. */
private fun slotAfter(
    active: Any?,
    navigation: SlotNavigator<Any>.(done: (Any) -> Unit) -> Unit,
): Pair<Any?, List<Any>> {
    val navigator = FakeSlotNavigator(active)
    val completions = mutableListOf<Any>()
    navigator.navigation { completions += it }
    return navigator.configuration to completions
}

/** A stack without components, which navigates at once. */
private class FakeStackNavigator(var stack: List<Any>) : StackNavigator<Any> {
    override fun navigate(
        transformer: (stack: List<Any>) -> List<Any>,
        onComplete: (newStack: List<Any>, oldStack: List<Any>) -> Unit,
    ) {
        val oldStack = stack
        stack = transformer(oldStack)
        onComplete(stack, oldStack)
    }
}

/** A slot without components, which navigates at once. */
private class FakeSlotNavigator(var configuration: Any?) : SlotNavigator<Any> {
    override fun navigate(
        transformer: (configuration: Any?) -> Any?,
        onComplete: (newConfiguration: Any?, oldConfiguration: Any?) -> Unit,
    ) {
        val oldConfiguration = configuration
        configuration = transformer(oldConfiguration)
        onComplete(configuration, oldConfiguration)
    }
}
