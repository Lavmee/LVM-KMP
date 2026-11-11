package tech.annexflow.lvm.maincontext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [block] on `Dispatchers.Main.immediate` and returns its result. The compiler plugin moves calls to
 * `@MainContext` functions into it.
 */
@MainContextCompilerApi
public suspend fun <T> onMainContext(block: suspend () -> T): T =
    withContext(Dispatchers.Main.immediate) { block() }
