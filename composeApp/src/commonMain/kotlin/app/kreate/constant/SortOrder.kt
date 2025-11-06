package app.kreate.constant

import org.jetbrains.annotations.Contract


enum class SortOrder( val rotationZ: Float, val asSqlString: String ) {
    ASCENDING( 0f, "ASC" ),
    DESCENDING( 180f, "DESC" );

    operator fun not() = when (this) {
        ASCENDING -> DESCENDING
        DESCENDING -> ASCENDING
    }

    /**
     * Attempt to apply sort order based on selected value.
     *
     * The provided list [items] is always assumed to be sorted
     * in ascending order. Therefore, it only get reversed
     * when [DESCENDING] is selected.
     *
     * Return list is always a new list to prevent unwanted results
     *
     * @return a new list regardless selected value
     */
    @Contract(
        value = "_->new",
        pure = true
    )
    fun <T> applyTo( items: List<T> ): List<T> =
        when( this ) {
            DESCENDING -> items.reversed()
            ASCENDING -> items.toList()
        }
}