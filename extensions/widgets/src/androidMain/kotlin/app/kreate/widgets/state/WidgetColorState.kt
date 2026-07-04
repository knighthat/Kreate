package app.kreate.widgets.state

import android.os.Bundle


data class WidgetColorState(
    val background: Long,
    val surface: Long,
    val onSurface: Long
) {

    companion object {

        // Copied from Media3's Util class
        private val BACKGROUND_FIELD = 0.toString(Character.MAX_RADIX)
        private val SURFACE_FIELD = 1.toString(Character.MAX_RADIX)
        private val ON_SURFACE_FIELD = 2.toString(Character.MAX_RADIX)

        /**
         * @throws IllegalArgumentException if bundle doesn't contain all colors
         */
        @Throws(IllegalArgumentException::class)
        fun fromBundle( bundle: Bundle ): WidgetColorState {
            require(
                bundle.containsKey(BACKGROUND_FIELD)
                        && bundle.containsKey(SURFACE_FIELD)
                        && bundle.containsKey(ON_SURFACE_FIELD)
            ) { "Bundle doesn't have all colors" }

            val background = bundle.getLong( BACKGROUND_FIELD )
            val surface = bundle.getLong( SURFACE_FIELD )
            val onSurface = bundle.getLong( ON_SURFACE_FIELD )
            return WidgetColorState(background, surface, onSurface)
        }
    }

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putLong( BACKGROUND_FIELD, background )
        bundle.putLong( SURFACE_FIELD, surface )
        bundle.putLong( ON_SURFACE_FIELD, onSurface )

        return bundle
    }
}