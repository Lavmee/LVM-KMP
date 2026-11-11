package tech.annexflow.lvm.maincontext.compiler

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory

/** Errors at calls that can't move to the main dispatcher. */
internal object MainContextErrors : KtDiagnosticsContainer() {
    val JUMP_OUT_OF_CALL by error0<PsiElement>()
    val MISSING_RUNTIME by error0<PsiElement>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = MainContextErrorMessages
}

private object MainContextErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("MainContext") { map ->
        map.put(
            MainContextErrors.JUMP_OUT_OF_CALL,
            "This @MainContext call can't move to the main dispatcher, because its receiver or arguments return, " +
                "break or continue out of it. Compute them in a variable before the call.",
        )
        map.put(
            MainContextErrors.MISSING_RUNTIME,
            "This @MainContext call can't move to the main dispatcher, because onMainContext of lvm-main-context " +
                "is not on the classpath. Add lvm-main-context of the same version as the compiler plugin.",
        )
    }
}
