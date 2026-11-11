package tech.annexflow.lvm.decompose

private const val HASH_CODE_RADIX = 16

/**
 * A key for a value that a component context passes down to the contexts of its children, like a `CompositionLocal`
 * in Compose. Declare one with [componentLocalOf], give it a value with [AppComponentContext.provide] or
 * [provideLocals], and read it with [ComponentLocals.get] or `by` delegation.
 *
 * Locals suit values that the whole subtree shares, such as analytics, a theme or feature flags. Pass repositories and
 * other dependencies through constructors.
 */
public class ComponentLocal<T> internal constructor(
    private val name: String?,
    private val defaultFactory: (() -> T)?,
) {

    internal fun default(): T {
        val factory = checkNotNull(defaultFactory) { "$this has no value and no default" }
        return factory()
    }

    override fun toString(): String =
        if (name != null) "ComponentLocal($name)" else "ComponentLocal@${hashCode().toString(HASH_CODE_RADIX)}"
}

/**
 * A new [ComponentLocal]. [name] appears in error messages. [default] produces the value of a context that has none;
 * without it, reading the local there fails with an [IllegalStateException].
 */
public fun <T> componentLocalOf(name: String? = null, default: (() -> T)? = null): ComponentLocal<T> =
    ComponentLocal(name, default)

/** A value for a [ComponentLocal], made with [provides] and passed to [provideLocals]. */
public class ProvidedValue<T> internal constructor(
    internal val local: ComponentLocal<T>,
    internal val value: T,
)

/** Pairs this local with [value], for [provideLocals]. */
public infix fun <T> ComponentLocal<T>.provides(value: T): ProvidedValue<T> = ProvidedValue(this, value)
