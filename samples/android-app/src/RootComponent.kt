package tech.annexflow.lvm.samples.android

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import tech.annexflow.lvm.decompose.AppComponentContext

/** A list of colors and the details of one, in a Decompose stack that survives process death. */
class RootComponent(context: AppComponentContext) : AppComponentContext by context {

    private val navigation = StackNavigation<Config>()

    val stack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Colors,
        handleBackButton = true,
        childFactory = { config, _ -> child(config) },
    )

    private fun child(config: Config): Child = when (config) {
        Config.Colors -> Child.Colors(COLORS) { color -> navigation.pushNew(Config.Details(color)) }
        is Config.Details -> Child.Details(config.color) { navigation.pop() }
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data object Colors : Config

        @Serializable
        data class Details(val color: String) : Config
    }

    sealed interface Child {
        class Colors(val colors: List<String>, val onSelect: (String) -> Unit) : Child

        class Details(val color: String, val onBack: () -> Unit) : Child
    }

    private companion object {
        val COLORS = listOf("Red", "Green", "Blue")
    }
}
