package tech.annexflow.lvm.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import tech.annexflow.lvm.core.Loadable

class LoadableFlowTest {

    private val failure = IllegalStateException()

    @Test
    fun `mapLoaded and unwrap follow the source flow`() {
        val source = MutableStateFlow<Loadable<Int>>(Loadable.Loading)
        val mapped = source.mapLoaded { it * 2 }
        val unwrapped = source.unwrap()

        assertEquals(Loadable.Loading, mapped.value)
        assertNull(unwrapped.value)
        source.value = Loadable.Loaded(21)
        assertEquals(Loadable.Loaded(42), mapped.value)
        assertEquals(21, unwrapped.value)
    }

    @Test
    fun `awaitLoaded skips failures and returns the loaded state`() = runTest {
        val source = MutableStateFlow<Loadable<Int>>(Loadable.Loading)
        launch {
            delay(1_000)
            source.value = Loadable.Failed(failure)
            delay(1_000)
            source.value = Loadable.Loaded(1)
        }

        assertEquals(Loadable.Loaded(1), source.awaitLoaded())
    }

    @Test
    fun `awaitDone returns the first finished state including failures`() = runTest {
        val source = MutableStateFlow<Loadable<Int>>(Loadable.Loading)
        launch {
            delay(1_000)
            source.value = Loadable.Failed(failure)
        }

        assertEquals(Loadable.Failed(failure), source.awaitDone())
    }
}
