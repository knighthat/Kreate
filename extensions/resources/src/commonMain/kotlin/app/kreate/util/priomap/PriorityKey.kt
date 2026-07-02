package app.kreate.util.priomap


/**
 * A composite key used to uniquely identify and sort items within the priority map.
 *
 * @property priority The [Priority] level determining the primary sorting group.
 * @property sequence A strictly increasing identifier used to preserve insertion order
 * if two keys share the exact same [Priority].
 */
data class PriorityKey(
    val priority: Priority,
    val sequence: Long
) : Comparable<PriorityKey> {

    /**
     * Compares this key with another [PriorityKey] to determine sorting placement.
     *
     * **Priority Ordering:** Descending ([Priority.HIGH] -> [Priority.NORMAL] -> [Priority.LOW]).
     *
     * Higher priority items are placed at the beginning (top) of the map.
     *
     * **Insertion Ordering:** Ascending chronologically. If priorities are identical, the item
     * with the lower [sequence] (added earlier) is placed first, ensuring stable insertion order.
     *
     * @param other The other key instance to compare against.
     * @return A negative integer if this key should appear before [other], zero if they are identical,
     * or a positive integer if this key should appear after [other].
     */
    override fun compareTo( other: PriorityKey ): Int {
        // First sort by Priority enum order (HIGH -> LOW)
        val priorityCompare = other.priority.compareTo( this.priority )
        if( priorityCompare != 0 ) return priorityCompare

        // If priorities are equal, sort by insertion order [sequence]
        return this.priority.compareTo( other.priority )
    }
}
