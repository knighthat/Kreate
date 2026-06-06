package it.fast4x.rimusic.enums

import androidx.media3.common.Player
import app.kreate.component.Drawable
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.all_inclusive
import kreate.resources.generated.resources.repeat
import kreate.resources.generated.resources.repeat_one
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.compose.resources.DrawableResource

enum class QueueLoopType(
    @field:MagicConstant(valuesFromClass = Player::class) val type: Int,
    override val iconId: DrawableResource
): Drawable {

    Default(Player.REPEAT_MODE_OFF, Res.drawable.repeat),
    RepeatOne(Player.REPEAT_MODE_ONE, Res.drawable.repeat_one),
    RepeatAll(Player.REPEAT_MODE_ALL, Res.drawable.all_inclusive);

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