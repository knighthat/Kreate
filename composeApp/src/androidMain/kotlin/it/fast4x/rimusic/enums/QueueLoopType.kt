package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.media3.common.Player
import app.kreate.android.R
import org.intellij.lang.annotations.MagicConstant

enum class QueueLoopType(
    @field:MagicConstant(valuesFromClass = Player::class) val type: Int,
    @field:DrawableRes override val androidIconId: Int
): Drawable {

    Default( Player.REPEAT_MODE_OFF, R.drawable.repeat ),
    RepeatOne( Player.REPEAT_MODE_ONE, R.drawable.repeatone ),
    RepeatAll( Player.REPEAT_MODE_ALL, R.drawable.infinite );

    /**
     * Go through all values of [QueueLoopType] from top to bottom.
     *
     * Functions similarly to an iterator, but this is an infinite loop.
     *
     * Once pointer reaches the end (last value), [next] will return
     * back the the first value.
     */
    fun next(): QueueLoopType = when( this ) {
        // Avoid using `else` to make sure each
        // value has their own next element
        Default -> RepeatOne
        RepeatOne -> RepeatAll
        RepeatAll -> Default
    }

    companion object {
        @JvmStatic
        fun from(value: Int): QueueLoopType {
            return when (value) {
                Player.REPEAT_MODE_OFF -> Default
                Player.REPEAT_MODE_ONE -> RepeatOne
                Player.REPEAT_MODE_ALL -> RepeatAll
                else -> Default
            }
        }
    }
}