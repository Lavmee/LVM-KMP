package tech.annexflow.lvm.decompose

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * A screen here is a [PagingDataPresenter] built the way `LazyPagingItems` builds one: with the first page of the
 * replay cache when the flow is a `SharedFlow`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagingTest {

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    /** The write lands while no screen watches, and the new read hangs, as on a phone after a form closes. */
    @Test
    fun `a list that comes back shows its last rows while the database is read again`() = runTest {
        val database = Database("Row B", "Row A")
        val scope = pagingScope()
        val paging = paging(flow = database.pages(), coroutineScope = scope)

        val firstScreen = screen(paging.flow)
        val firstShow = scope.launch { paging.flow.collectLatest(firstScreen::collectFrom) }
        advanceUntilIdle()
        assertEquals(listOf("Row B", "Row A"), firstScreen.snapshot().items)

        // Another screen covers the list: its composition is removed and its collection cancelled.
        firstShow.cancel()
        advanceUntilIdle()
        val slowRead = CompletableDeferred<Unit>()
        database.slowRead = slowRead
        database.insert("Row C")
        advanceUntilIdle()
        assertEquals(1, database.invalidatedSources)

        // The list comes back with a new presenter, which starts with the cached page.
        val secondScreen = screen(paging.flow)
        assertEquals(listOf("Row B", "Row A"), secondScreen.snapshot().items)

        val secondShow = scope.launch { paging.flow.collectLatest(secondScreen::collectFrom) }
        advanceUntilIdle()
        assertEquals(1, database.hangingReads)
        assertEquals(listOf("Row B", "Row A"), secondScreen.snapshot().items)

        slowRead.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Row C", "Row B", "Row A"), secondScreen.snapshot().items)

        secondShow.cancel()
        scope.cancel()
    }

    /** Repositories do work on every subscription, so a list that is shown again subscribes again. */
    @Test
    fun `the database is read only while a screen watches and again when one comes back`() = runTest {
        val database = Database("Row A")
        val scope = pagingScope()
        val paging = paging(flow = database.pages(), coroutineScope = scope)

        val firstShow = scope.launch { paging.flow.collectLatest(screen(paging.flow)::collectFrom) }
        advanceUntilIdle()
        assertEquals(1, database.subscriptions)
        assertEquals(1, database.liveSubscriptions)

        firstShow.cancel()
        advanceUntilIdle()
        assertEquals(0, database.liveSubscriptions)

        val secondShow = scope.launch { paging.flow.collectLatest(screen(paging.flow)::collectFrom) }
        advanceUntilIdle()
        assertEquals(2, database.subscriptions)
        assertEquals(1, database.liveSubscriptions)

        secondShow.cancel()
        scope.cancel()
    }

    @Test
    fun `the snapshot is empty at first access and then holds the loaded items`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = pagingScope()
        val paging = paging(flow = Database("Row B", "Row A").pages(), coroutineScope = scope)

        assertEquals(emptyList(), paging.snapshotList.value.items)
        advanceUntilIdle()

        assertEquals(listOf("Row B", "Row A"), paging.snapshotList.value.items)
        assertEquals("Row A", paging.getElement(1))
        assertNull(paging.getElement(2))
        scope.cancel()
    }

    @Test
    fun `a stub paging holds its items at once`() {
        val paging = stubPaging(listOf("Row A", "Row B"))

        assertEquals(listOf("Row A", "Row B"), paging.snapshotList.value.items)
        assertEquals("Row B", paging.getElement(1))
        assertNull(paging.getElement(2))
        assertTrue(paging.flow is SharedFlow<*>)
    }

    @Test
    fun `a loaded paging flow gives a new screen its rows and a finished load at once`() = runTest {
        val flow = loadedPagingFlow(listOf("Row A"))
        val screen = screen(flow)
        val show = launch { screen.collectFrom((flow as SharedFlow<PagingData<String>>).replayCache.single()) }
        advanceUntilIdle()

        assertEquals(listOf("Row A"), screen.snapshot().items)
        assertTrue(screen.loadStateFlow.value?.refresh?.endOfPaginationReached == true)
        show.cancel()
    }

    /** The component's scope, on the test's scheduler so that advanceUntilIdle moves it too. */
    private fun TestScope.pagingScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun TestScope.screen(flow: Flow<PagingData<String>>): PagingDataPresenter<String> =
        object : PagingDataPresenter<String>(
            mainContext = StandardTestDispatcher(testScheduler),
            cachedPagingData = if (flow is SharedFlow<PagingData<String>>) flow.replayCache.firstOrNull() else null,
        ) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<String>) = Unit
        }

    /** A table behind pages that work like a database's: a write invalidates every source handed out. */
    private class Database(vararg rows: String) {

        private val rows = rows.toMutableList()
        private val sources = mutableListOf<PagingSource<Int, String>>()

        /** While set, reading a page waits for it: a slow query after a write. */
        var slowRead: CompletableDeferred<Unit>? = null

        var subscriptions = 0
            private set

        var liveSubscriptions = 0
            private set

        var hangingReads = 0
            private set

        val invalidatedSources: Int
            get() = sources.count { it.invalid }

        /** Every subscription builds a new Pager, as a repository behind a query does. */
        fun pages(): Flow<PagingData<String>> = flow {
            subscriptions++
            liveSubscriptions++
            try {
                emitAll(Pager(PagingConfig(pageSize = PAGE_SIZE)) { Source().also(sources::add) }.flow)
            } finally {
                liveSubscriptions--
            }
        }

        fun insert(row: String) {
            rows.add(index = 0, element = row)
            sources.forEach { it.invalidate() }
        }

        private inner class Source : PagingSource<Int, String>() {

            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
                slowRead?.let { read ->
                    hangingReads++
                    read.await()
                    hangingReads--
                }
                return LoadResult.Page(data = rows.toList(), prevKey = null, nextKey = null)
            }

            override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
