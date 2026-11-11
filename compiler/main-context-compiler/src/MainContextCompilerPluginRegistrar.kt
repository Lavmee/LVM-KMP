package tech.annexflow.lvm.maincontext.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

/** Registers the main-context transformation. The compiler finds this class through `META-INF/services`. */
public class MainContextCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "tech.annexflow.lvm.maincontext"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(MainContextIrGenerationExtension())
    }
}
