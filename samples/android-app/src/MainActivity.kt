package tech.annexflow.lvm.samples.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tech.annexflow.lvm.decompose.retainedAppComponent
import tech.annexflow.lvm.logger.console.consoleLogger

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = retainedAppComponent(consoleLogger()) { context -> RootComponent(context) }
        setContent { RootContent(root) }
    }
}
