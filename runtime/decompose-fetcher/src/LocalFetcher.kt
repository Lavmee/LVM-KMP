package tech.annexflow.lvm.decompose.fetcher

import tech.annexflow.lvm.decompose.AppComponentContext
import tech.annexflow.lvm.decompose.ComponentLocal
import tech.annexflow.lvm.decompose.componentLocalOf

/**
 * The [Fetcher] of a component tree. The root provides it, for example with
 * `provideLocals(LocalFetcher provides Fetcher(httpClient))`, and components read it with [fetcher].
 */
public val LocalFetcher: ComponentLocal<Fetcher> = componentLocalOf(name = "Fetcher")

/**
 * The [Fetcher] that the root provided under [LocalFetcher].
 *
 * @throws IllegalStateException if the root provided none.
 */
public val AppComponentContext.fetcher: Fetcher
    get() = locals[LocalFetcher]
