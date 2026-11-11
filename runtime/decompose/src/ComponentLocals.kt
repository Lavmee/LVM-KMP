package tech.annexflow.lvm.decompose

import kotlin.reflect.KProperty

/** The values of [ComponentLocal]s that a component context holds. It never changes; adding values makes a copy. */
public class ComponentLocals private constructor(
    private val values: Map<ComponentLocal<*>, Any?>,
) {

    /** Whether this holds a value for [local]. A local with a default has a value even when this returns `false`. */
    public operator fun contains(local: ComponentLocal<*>): Boolean = local in values

    /**
     * The value of [local], or its default when this holds none.
     *
     * @throws IllegalStateException if this holds no value for [local] and it has no default.
     */
    public operator fun <T> get(local: ComponentLocal<T>): T {
        if (local !in values) return local.default()
        // provide and provides pair every local with a value of its own type.
        @Suppress("UNCHECKED_CAST")
        return values[local] as T
    }

    internal fun with(added: Array<out ProvidedValue<*>>): ComponentLocals =
        if (added.isEmpty()) this else ComponentLocals(values + added.map { it.local to it.value })

    public companion object {
        /** No values: every local returns its default. */
        public val Empty: ComponentLocals = ComponentLocals(emptyMap())
    }
}

/** Holds [locals]. Every [AppComponentContext] is one. */
public interface ComponentLocalsOwner {
    public val locals: ComponentLocals
}

/** Reads this local from the owner's [ComponentLocalsOwner.locals] at every access: `val theme by ThemeLocal`. */
public operator fun <T> ComponentLocal<T>.getValue(thisRef: ComponentLocalsOwner, property: KProperty<*>): T =
    thisRef.locals[this]
