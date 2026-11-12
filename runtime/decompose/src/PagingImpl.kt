package tech.annexflow.lvm.decompose

import androidx.paging.ItemSnapshotList
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Decompose removes the composition of a screen that another screen covers, and `LazyPagingItems` lives in
 * `remember`, so a screen that comes back builds a new, empty presenter. It starts with rows only when its flow is a
 * `SharedFlow` with a page in the replay cache: `LazyPagingItems` passes that page to the presenter as
 * `cachedPagingData`. So [flow] is [pages], a hot flow that replays the last page the screen got.
 *
 * The upstream is collected only while [pages] has collectors. While no screen watches, a database write would
 * otherwise put a new generation into the replay cache that nobody has read, and a screen coming back would start
 * empty again; repositories that do work on every subscription would do it only once per component; and a hidden list
 * would be read again on every write. Each period of collection has its own `cachedIn`, because one `PagingData` is
 * collected by several presenters (the screen, the snapshot and a screen that comes back) and a generation of a
 * `Pager` can't be collected twice.
 *
 * The snapshot has its own presenter, which collects [pages] for the rest of the component's life. It is created on
 * first access, so only components that use the snapshot keep reading the upstream.
 */
internal class PagingImpl<T : Any>(
    private val coroutineScope: CoroutineScope,
    upstream: Flow<PagingData<T>>,
) : Paging<T> {

    // PagingData is "latest wins": replay one and never make the sender wait for a slow collector.
    private val pages = MutableSharedFlow<PagingData<T>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val flow: Flow<PagingData<T>> = pages.asSharedFlow()

    init {
        coroutineScope.launch {
            pages.subscriptionCount
                .map { subscribers -> subscribers > 0 }
                .distinctUntilChanged()
                .collectLatest { isWatched ->
                    if (isWatched) coroutineScope { upstream.cachedIn(scope = this).collect(pages) }
                }
        }
    }

    private val presentation by lazy {
        Presentation().also { presentation ->
            coroutineScope.launch {
                flow.collectLatest { pagingData -> presentation.presenter.collectFrom(pagingData) }
            }
        }
    }

    override val snapshotList: StateFlow<ItemSnapshotList<T>>
        get() = presentation.snapshotList

    override fun getElement(index: Int): T? =
        try {
            presentation.presenter[index]
        } catch (_: IndexOutOfBoundsException) {
            // The snapshot can shrink between reading its size and reading an item; that is not a failure.
            null
        }

    private inner class Presentation {

        val presenter: PagingDataPresenter<T> = object : PagingDataPresenter<T>() {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
                mutableSnapshotList.update { snapshot() }
            }
        }

        private val mutableSnapshotList: MutableStateFlow<ItemSnapshotList<T>> = MutableStateFlow(presenter.snapshot())

        val snapshotList: StateFlow<ItemSnapshotList<T>> = mutableSnapshotList.asStateFlow()
    }
}
