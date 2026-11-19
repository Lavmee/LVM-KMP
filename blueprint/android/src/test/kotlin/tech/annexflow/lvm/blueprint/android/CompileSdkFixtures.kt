package tech.annexflow.lvm.blueprint.android

/**
 * Prints the compile SDK by the name of its platform, such as `android-37.1`, prefixed with `lvm-compile-sdk`. The
 * callback runs after lvm's finalizeDsl. AGP's DSL has no getter for the name, so the snippet calls the one of AGP's
 * implementation. From level 37 on, the name has a minor level, which is 0 while none is set: `android-37.0`.
 */
const val PRINT_COMPILE_SDK: String = """
androidComponents.finalizeDsl { android ->
    println("lvm-compile-sdk " + android.javaClass.getMethod("getCompileSdkVersion").invoke(android))
}
"""

/** The compile SDK that [PRINT_COMPILE_SDK] printed in [output]. */
fun compileSdkIn(output: String): String =
    output.lines().single { it.startsWith("lvm-compile-sdk ") }.removePrefix("lvm-compile-sdk ")
