package tech.annexflow.lvm.decompose

import androidx.paging.ItemSnapshotList
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A page of [items] whose loading is done. `PagingData.from(items)` without load states leaves `refresh` at `Loading`
 * forever, so a screen that waits for the end of loading would show neither the list nor an empty state.
 */
public fun <T : Any> loadedPagingData(items: List<T>): PagingData<T> = PagingData.from(
    data = items,
    sourceLoadStates = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = true),
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    ),
)

/** An empty page whose loading is done: an empty list, not one that loads forever. */
public fun <T : Any> emptyPagingData(): PagingData<T> = loadedPagingData(emptyList())

/**
 * A flow that already holds [data] in its replay cache, as [Paging.flow] does. `LazyPagingItems` takes the page from
 * the cache when it is created, so previews and UI tests show it without waiting for a dispatcher, which a cold
 * `flowOf(data)` needs.
 */
public fun <T : Any> pagingFlowOf(data: PagingData<T>): Flow<PagingData<T>> =
    MutableSharedFlow<PagingData<T>>(replay = 1).apply { tryEmit(data) }

/** [pagingFlowOf] a [loadedPagingData] of [items]. */
public fun <T : Any> loadedPagingFlow(items: List<T>): Flow<PagingData<T>> = pagingFlowOf(loadedPagingData(items))

/** [pagingFlowOf] an [emptyPagingData]. */
public fun <T : Any> emptyPagingFlow(): Flow<PagingData<T>> = loadedPagingFlow(emptyList())

/**
 * [Paging] of [items] for fakes and preview components, without coroutines: its flow is a [loadedPagingFlow], and its
 * snapshot and elements are [items] at once.
 */
public fun <T : Any> stubPaging(items: List<T> = emptyList()): Paging<T> = object : Paging<T> {

    override val flow: Flow<PagingData<T>> = loadedPagingFlow(items)

    override val snapshotList: StateFlow<ItemSnapshotList<T>> =
        MutableStateFlow(ItemSnapshotList(placeholdersBefore = 0, placeholdersAfter = 0, items = items))

    override fun getElement(index: Int): T? = items.getOrNull(index)
}
