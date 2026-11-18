package tech.annexflow.lvm.decompose.fetcher

import com.github.michaelbull.retry.policy.RetryPolicy
import tech.annexflow.lvm.decompose.ComponentLocal
import tech.annexflow.lvm.decompose.componentLocalOf

/**
 * The retry policy of [fetcherState] and [fetcherResultState] in the components of a tree: [Fetcher.retryPolicy]
 * unless the tree provides another one, so previews and tests need no setup.
 *
 * The root sets the policy of the whole tree once, next to [LocalFetcher]:
 *
 * ```kotlin
 * val retryPolicy = stopAtAttempts<Throwable>(4) + equalJitterBackoff(min = 1_000, max = 30_000)
 * val root = context.provideLocals(
 *     LocalFetcher provides Fetcher(httpClient),
 *     LocalFetcherRetryPolicy provides retryPolicy.stopAtCumulativeDelay(60_000),
 * )
 * ```
 *
 * A parent gives its subtree a policy of its own the same way, and a `retryPolicy` argument wins over both.
 */
public val LocalFetcherRetryPolicy: ComponentLocal<RetryPolicy<Throwable>> =
    componentLocalOf(name = "FetcherRetryPolicy") { Fetcher.retryPolicy }
