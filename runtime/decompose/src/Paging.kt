package tech.annexflow.lvm.decompose

import androidx.paging.ItemSnapshotList
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The pages of a component's list: [flow] for the screen, and on demand a [snapshotList] of the loaded items.
 *
 * Create one with [paging].
 */
public interface Paging<T : Any> {

    /**
     * The pages for the screen, for example for `collectAsLazyPagingItems`. It is a `SharedFlow` that replays the last
     * page it delivered, so a screen that is composed again shows its rows from the first frame instead of an empty
     * list. The upstream is collected only while this flow has collectors.
     */
    public val flow: Flow<PagingData<T>>

    /**
     * The items loaded so far. The first access starts collecting [flow] for the rest of the component's life, and
     * the snapshot fills in on a later turn of the main thread, so the value read right after the first access is
     * empty. Read it in a collector, not once in a click handler.
     */
    public val snapshotList: StateFlow<ItemSnapshotList<T>>

    /** The loaded item at [index], or `null` outside the loaded items. Like [snapshotList], it starts collecting. */
    public fun getElement(index: Int): T?
}

/** [Paging] of [flow] in [componentScope][CoroutineScopeOwner.componentScope]. */
public fun <T : Any> CoroutineScopeOwner.paging(flow: Flow<PagingData<T>>): Paging<T> =
    paging(flow = flow, coroutineScope = componentScope)

/** [Paging] of [flow] in [coroutineScope], which must live as long as the screen that shows it. */
public fun <T : Any> paging(flow: Flow<PagingData<T>>, coroutineScope: CoroutineScope): Paging<T> =
    PagingImpl(coroutineScope = coroutineScope, upstream = flow)
