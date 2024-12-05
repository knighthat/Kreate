package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

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
            Unlimited -> stringResource( R.string.unlimited)
            else -> this.name
        }

    fun toInt(): Int = when( this ) {
        Unlimited -> 1_000_000      // YES! This is a valid format in Kotlin
        else -> this.name.toInt()
    }
}