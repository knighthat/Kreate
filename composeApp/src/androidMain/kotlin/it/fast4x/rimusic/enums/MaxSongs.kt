package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.word_unlimited
import org.jetbrains.compose.resources.stringResource

enum class MaxSongs: TextView {

    `50`,
    `100`,
    `200`,
    `300`,
    `500`,
    `1000`,
    `2000`,
    `3000`,
    Unlimited;

    override val text: String
        @Composable
        get() = when( this ) {
            Unlimited -> stringResource(Res.string.word_unlimited )
            else -> this.name
        }

    fun toInt(): Int = when( this ) {
        Unlimited -> 1_000_000      // YES! This is a valid format in Kotlin
        else -> this.name.toInt()
    }
}