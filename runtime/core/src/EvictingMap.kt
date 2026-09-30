package tech.annexflow.lvm.core

/**
 * A [MutableMap] that keeps at most [maxSize] entries.
 *
 * When a `put` or `putAll` makes the map larger than [maxSize], the entries inserted first are removed. Replacing the
 * value of an existing key keeps the key's position.
 */
public sealed interface EvictingMap<K, V> : MutableMap<K, V> {
    /** The largest number of entries this map keeps. */
    public val maxSize: Int
}

/** Creates an empty [EvictingMap] that keeps at most [maxSize] entries. [maxSize] must be positive. */
public fun <K, V> EvictingMap(maxSize: Int): EvictingMap<K, V> = EvictingMapImpl(maxSize)

private class EvictingMapImpl<K, V>(
    override val maxSize: Int,
    private val implementation: MutableMap<K, V> = LinkedHashMap(),
) : EvictingMap<K, V>, MutableMap<K, V> by implementation {
    init {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
    }

    override fun put(key: K, value: V): V? {
        val result = implementation.put(key, value)
        trimToSize()
        return result
    }

    override fun putAll(from: Map<out K, V>) {
        implementation.putAll(from)
        trimToSize()
    }

    // Delegation with `by` never forwards these, so without them the map would compare by identity.
    override fun equals(other: Any?): Boolean = other === this || implementation == other

    override fun hashCode(): Int = implementation.hashCode()

    override fun toString(): String = implementation.toString()

    private fun trimToSize() {
        while (size > maxSize) {
            remove(entries.first().key)
        }
    }
}
