package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import app.kreate.component.TextView

enum class MaxTopPlaylistItems: TextView {
    `10`,
    `20`,
    `30`,
    `40`,
    `50`,
    `70`,
    `90`,
    `100`,
    `150`,
    `200`;

    override val text: String
        @Composable
        get() = this.name

    fun toInt(): Int = this.name.toInt()
}