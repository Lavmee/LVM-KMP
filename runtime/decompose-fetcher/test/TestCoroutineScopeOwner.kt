package tech.annexflow.lvm.decompose.fetcher

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.annexflow.lvm.decompose.CoroutineScopeOwner
import tech.annexflow.lvm.decompose.PreviewAppComponentContext

/**
 * A component whose `launchIO` runs on `Dispatchers.Main`, which the test sets to a test dispatcher: the test decides
 * when loads run, and retry delays take virtual time.
 */
class TestCoroutineScopeOwner(
    lifecycle: Lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED),
) : CoroutineScopeOwner by PreviewAppComponentContext(lifecycle = lifecycle) {

    override fun launchIO(block: suspend CoroutineScope.() -> Unit): Job = componentScope.launch(block = block)
}
