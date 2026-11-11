package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComponentLocalsTest {

    @Test
    fun `a provided value reaches contexts two levels down`() {
        val root = rootContext()
        root.provide(greeting, "hello")

        val grandchild = root.childContext("child").childContext("grandchild")

        assertEquals("hello", grandchild.locals[greeting])
    }

    @Test
    fun `provideLocals overrides a subtree without changing the parent or siblings`() {
        val root = rootContext()
        root.provide(greeting, "root")
        val branch = root.childContext("branch")
        val sibling = root.childContext("sibling")

        val overridden = branch.provideLocals(greeting provides "branch")
        val overriddenChild = overridden.childContext("branch-child")

        assertEquals("branch", overridden.locals[greeting])
        assertEquals("branch", overriddenChild.locals[greeting])
        assertEquals("root", branch.locals[greeting])
        assertEquals("root", sibling.locals[greeting])
        assertEquals("root", root.locals[greeting])
    }

    @Test
    fun `a local without a value returns its default and fails without one`() {
        val withDefault = componentLocalOf(name = "withDefault") { "default" }
        val root = rootContext()

        assertEquals("default", root.locals[withDefault])
        assertFailsWith<IllegalStateException> { root.locals[greeting] }
    }

    @Test
    fun `provide fails once a child context was created`() {
        val root = rootContext()
        root.childContext("child")

        assertFailsWith<IllegalStateException> { root.provide(greeting, "too late") }
    }

    @Test
    fun `a value provided before childStack reaches its first child`() {
        val root = rootContext()
        root.provide(greeting, "visible")

        val stack = root.childStack(
            source = StackNavigation<Unit>(),
            serializer = null,
            initialConfiguration = Unit,
            childFactory = { _, context -> context.locals[greeting] },
        )

        assertEquals("visible", stack.value.active.instance)
    }

    @Test
    fun `provide fails once childStack created its first child`() {
        val root = rootContext()
        root.childStack(
            source = StackNavigation<Unit>(),
            serializer = null,
            initialConfiguration = Unit,
            childFactory = { _, _ -> },
        )

        assertFailsWith<IllegalStateException> { root.provide(greeting, "too late") }
    }

    @Test
    fun `a component that delegates to its context provides and reads values`() {
        val component = DelegatingComponent(rootContext())

        component.provideGreeting("delegated")

        assertEquals("delegated", component.greetingValue)
    }

    @Test
    fun `provideLocals leaves the receiver open to provide`() {
        val root = rootContext()
        root.provideLocals(greeting provides "copy").childContext("child")

        root.provide(greeting, "root")

        assertEquals("root", root.locals[greeting])
    }

    @Test
    fun `provideLocals on a delegating component reaches the children of the copy`() {
        val component = DelegatingComponent(rootContext())
        component.provideGreeting("component")

        val child = component.provideLocals(greeting provides "override").childContext("child")

        assertEquals("override", child.locals[greeting])
        assertEquals("component", component.greetingValue)
    }
}

private val greeting = componentLocalOf<String>(name = "greeting")

private class DelegatingComponent(context: AppComponentContext) : AppComponentContext by context {

    val greetingValue: String by greeting

    fun provideGreeting(value: String) {
        provide(greeting, value)
    }
}
