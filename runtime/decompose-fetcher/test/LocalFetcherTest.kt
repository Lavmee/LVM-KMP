package tech.annexflow.lvm.decompose.fetcher

import com.arkivanov.decompose.childContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import tech.annexflow.lvm.decompose.PreviewAppComponentContext
import tech.annexflow.lvm.decompose.provideLocals
import tech.annexflow.lvm.decompose.provides

class LocalFetcherTest {

    @Test
    fun `components get the fetcher the root provided`() {
        val fetcher = Fetcher(HttpClient(MockEngine { respondOk() }))
        val root = PreviewAppComponentContext().provideLocals(LocalFetcher provides fetcher)

        assertSame(fetcher, root.childContext("child").fetcher)
    }

    @Test
    fun `reading the fetcher fails when the root provided none`() {
        assertFailsWith<IllegalStateException> { PreviewAppComponentContext().fetcher }
    }
}
