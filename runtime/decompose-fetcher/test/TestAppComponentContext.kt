package tech.annexflow.lvm.decompose.fetcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.annexflow.lvm.decompose.AppComponentContext
import tech.annexflow.lvm.decompose.PreviewAppComponentContext

/**
 * A component on [context], with its locals, whose `launchIO` runs on `Dispatchers.Main` as in
 * [TestCoroutineScopeOwner]: the test decides when loads run, and retry delays take virtual time.
 */
class TestAppComponentContext(
    context: AppComponentContext = PreviewAppComponentContext(),
) : AppComponentContext by context {

    override fun launchIO(block: suspend CoroutineScope.() -> Unit): Job = componentScope.launch(block = block)
}
