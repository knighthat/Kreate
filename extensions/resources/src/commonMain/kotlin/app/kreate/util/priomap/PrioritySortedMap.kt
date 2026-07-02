package app.kreate.util.priomap


/**
 * Expected factory function to instantiate a platform-specific self-sorting mutable map.
 * Each target platform must provide an implementation (e.g., using `java.util.TreeMap` on JVM/Android)
 * to avoid manual, heavy arrays/list re-sorting in common code.
 * @param V The type of map values.
 * @return A [MutableMap] implementation that automatically preserves its keys' sorted order.
 */
expect fun <V> createPrioritySortedMap(): MutableMap<PriorityKey, V>