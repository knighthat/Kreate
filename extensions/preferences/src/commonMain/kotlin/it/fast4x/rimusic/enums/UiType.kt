package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import app.kreate.component.TextView
import app.kreate.preferences.Preferences

enum class UiType: TextView {
    RiMusic,
    ViMusic;

    companion object {

        fun current(): UiType = Preferences.MAIN_THEME.value
    }

    override val text: String
        @Composable
        get() = this.name

    fun isCurrent(): Boolean = current() == this

    fun isNotCurrent(): Boolean = !isCurrent()
}