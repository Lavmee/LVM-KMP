package tech.annexflow.lvm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadableTest {

    private val failure = IllegalStateException()

    @Test
    fun `map transforms loaded data and passes loading and failures through`() {
        val loaded: Loadable<Int> = Loadable.Loaded(2)
        val loading: Loadable<Int> = Loadable.Loading
        val error: Loadable<Int> = Loadable.Failed(failure)

        assertEquals(Loadable.Loaded("2"), loaded.map { it.toString() })
        assertEquals(Loadable.Loading, loading.map { it.toString() })
        assertEquals(Loadable.Failed(failure), error.map { it.toString() })
    }

    @Test
    fun `mapList transforms every element of a loaded list`() {
        val loaded: Loadable<List<Int>> = Loadable.Loaded(listOf(1, 2))
        val loading: Loadable<List<Int>> = Loadable.Loading

        assertEquals(Loadable.Loaded(listOf(10, 20)), loaded.mapList { it * 10 })
        assertEquals(Loadable.Loading, loading.mapList { it * 10 })
    }

    @Test
    fun `getters return the data only for loaded states`() {
        val loaded: Loadable<List<Int>> = Loadable.Loaded(listOf(1))
        val loading: Loadable<List<Int>> = Loadable.Loading
        val error: Loadable<List<Int>> = Loadable.Failed(failure)

        assertEquals(listOf(1), loaded.getOrNull())
        assertNull(loading.getOrNull())
        assertNull(error.getOrNull())
        assertEquals(listOf(1), loaded.getOrElse { listOf(2) })
        assertEquals(listOf(2), loading.getOrElse { listOf(2) })
        assertEquals(listOf(2), error.getOrElse { listOf(2) })
        assertEquals(listOf(1), loaded.getOrEmpty())
        assertEquals(emptyList<Int>(), loading.getOrEmpty())
        assertEquals(emptyList<Int>(), error.getOrEmpty())
    }
}
