package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import app.kreate.component.TextView

enum class PauseBetweenSongs: TextView {
    `0`,
    `5`,
    `10`,
    `15`,
    `20`,
    `30`,
    `40`,
    `50`,
    `60`;

    val asSeconds: Int = this.name.toInt()

    val asMillis: Long = this.asSeconds * 1000L

    override val text: String
        @Composable
        get() = "${this.name}s"
}
