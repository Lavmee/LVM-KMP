package tech.annexflow.lvm.maincontext.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBreakContinue
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isFunction
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * Moves calls to `@MainContext` functions from code that can suspend onto the main dispatcher:
 * `receiver.call(arguments)` becomes `onMainContext { receiver.call(arguments) }`.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class MainContextCallTransformer(
    private val context: IrPluginContext,
    private val file: IrFile,
) : IrElementTransformerVoid() {

    /**
     * One entry for each function and parameter around the visited code, innermost last: the function if code directly
     * in it can suspend, `null` otherwise.
     */
    private val scopes = ArrayDeque<IrFunction?>()

    /**
     * Lambdas passed to inline functions from code that can suspend. They are inlined there, so code in them can
     * suspend too.
     */
    private val inlinedLambdas = HashSet<IrSimpleFunction>()

    private val onMainContext: IrSimpleFunctionSymbol? by lazy {
        context.finderForSource(file).findFunctions(ON_MAIN_CONTEXT).singleOrNull()
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        scopes.addLast(declaration.takeIf { it.canSuspend() })
        try {
            return super.visitFunction(declaration)
        } finally {
            scopes.removeLast()
        }
    }

    // A default value can't suspend, even in a suspend function: Kotlin computes it in a stub that is not suspend.
    override fun visitValueParameter(declaration: IrValueParameter): IrStatement {
        scopes.addLast(null)
        try {
            return super.visitValueParameter(declaration)
        } finally {
            scopes.removeLast()
        }
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val function = scopes.lastOrNull()
        if (function != null) markInlinedLambdas(expression)
        expression.transformChildrenVoid()
        val moves = function != null && expression.symbol.owner.isMainContext()
        return if (moves) moveToMainContext(expression, checkNotNull(function)) else expression
    }

    private fun IrFunction.canSuspend(): Boolean =
        this is IrSimpleFunction &&
            // Adapters of function references: references to a @MainContext function run where they are called.
            origin != IrDeclarationOrigin.ADAPTER_FOR_CALLABLE_REFERENCE &&
            (isSuspend || this in inlinedLambdas)

    private fun markInlinedLambdas(call: IrCall) {
        val callee = call.symbol.owner
        if (!callee.isInline) return
        callee.parameters
            .filter { it.takesInlinedLambda() }
            .mapNotNullTo(inlinedLambdas) { (call.arguments[it] as? IrFunctionExpression)?.function }
    }

    private fun IrValueParameter.takesInlinedLambda(): Boolean =
        kind == IrParameterKind.Regular &&
            !isNoinline &&
            !isCrossinline &&
            type.isFunction() &&
            !type.isMarkedNullable()

    /** Wraps [call] in `onMainContext`, or reports why it can't and leaves it as it is. */
    private fun moveToMainContext(call: IrCall, parent: IrFunction): IrExpression {
        val onMainContext = onMainContext
        val error = when {
            call.jumpsOut() -> MainContextErrors.JUMP_OUT_OF_CALL
            onMainContext == null -> MainContextErrors.MISSING_RUNTIME
            else -> return wrap(call, parent, onMainContext)
        }
        context.diagnosticReporter.at(call, file).report(error)
        return call
    }

    // The new lambda and call take the offsets of the original call, so debuggers and stack traces show its line.
    private fun wrap(call: IrCall, parent: IrFunction, onMainContext: IrSimpleFunctionSymbol): IrExpression {
        val type = call.type
        val block = context.irFactory.buildFun {
            startOffset = call.startOffset
            endOffset = call.endOffset
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            name = SpecialNames.ANONYMOUS
            visibility = DescriptorVisibilities.LOCAL
            returnType = type
            isSuspend = true
        }
        block.body = DeclarationIrBuilder(context, block.symbol, call.startOffset, call.endOffset).irBlockBody {
            +irReturn(call)
        }
        block.patchDeclarationParents(parent)

        val lambda = IrFunctionExpressionImpl(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = context.irBuiltIns.suspendFunctionN(0).typeWith(type),
            function = block,
            origin = IrStatementOrigin.LAMBDA,
        )
        val builder = DeclarationIrBuilder(context, parent.symbol, call.startOffset, call.endOffset)
        return builder.irCall(onMainContext, type).apply {
            typeArguments[0] = type
            arguments[0] = lambda
        }
    }

    private fun IrSimpleFunction.isMainContext(visited: MutableSet<IrSimpleFunction> = HashSet()): Boolean =
        visited.add(this) && (hasAnnotation(MAIN_CONTEXT) || overriddenSymbols.any { it.owner.isMainContext(visited) })

    private companion object {
        val MAIN_CONTEXT = FqName("tech.annexflow.lvm.maincontext.MainContext")
        val ON_MAIN_CONTEXT = CallableId(FqName("tech.annexflow.lvm.maincontext"), Name.identifier("onMainContext"))
    }
}

/**
 * Whether the call returns, breaks or continues to a target outside of itself. The lambda that the call moves into
 * can't jump there.
 */
private fun IrCall.jumpsOut(): Boolean {
    val targets = HashSet<IrReturnTargetSymbol>()
    val loops = HashSet<IrLoop>()
    var jumpsOut = false
    acceptVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitFunction(declaration: IrFunction) {
                targets += declaration.symbol
                super.visitFunction(declaration)
            }

            override fun visitReturnableBlock(expression: IrReturnableBlock) {
                targets += expression.symbol
                super.visitReturnableBlock(expression)
            }

            override fun visitLoop(loop: IrLoop) {
                loops += loop
                super.visitLoop(loop)
            }

            override fun visitReturn(expression: IrReturn) {
                if (expression.returnTargetSymbol !in targets) jumpsOut = true
                super.visitReturn(expression)
            }

            override fun visitBreakContinue(jump: IrBreakContinue) {
                if (jump.loop !in loops) jumpsOut = true
                super.visitBreakContinue(jump)
            }
        },
    )
    return jumpsOut
}
