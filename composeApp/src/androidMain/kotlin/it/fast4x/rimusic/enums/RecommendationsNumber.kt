package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import app.kreate.component.TextView

enum class RecommendationsNumber: TextView {
    `5`,
    `10`,
    `15`,
    `20`;

    override val text: String
        @Composable
        get() = this.name

    fun toInt(): Int = this.name.toInt()
}
