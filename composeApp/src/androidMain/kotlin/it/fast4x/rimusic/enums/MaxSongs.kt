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

    val number: Long
        get() = when (this) {
            `50` -> 50
            `100` -> 100
            `200` -> 200
            `300` -> 300
            `500` -> 500
            `1000` -> 1000
            `2000` -> 2000
            `3000` -> 3000
            Unlimited -> 1000000
        } * 1L

    override val text: String
        @Composable
        get() = when( this ) {
            Unlimited -> stringResource( R.string.unlimited)
            else -> this.name
        }
}