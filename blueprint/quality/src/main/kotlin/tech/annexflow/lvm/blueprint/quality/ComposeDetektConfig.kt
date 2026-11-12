package tech.annexflow.lvm.blueprint.quality

/**
 * detekt config for a module that applies the compose plugin: composable functions are named like types, so
 * `FunctionNaming` ignores functions annotated with `@Composable`. The quality plugin puts it after the flat layout's
 * config and before the build's own config files, so those can override it.
 */
internal fun composeDetektConfig(): String = """
    |naming:
    |  FunctionNaming:
    |    ignoreAnnotated: ['Composable']
    |""".trimMargin()
