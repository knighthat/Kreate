package me.knighthat.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

interface TextView {

    @get:StringRes
    val textId: Int
        get() = throw NotImplementedError("""
                This setting uses [${this::class.simpleName}#text] directly 
                or its [${this::class.simpleName}#textId] hasn't initialized!
        """.trimIndent())

    val text: String
        @Composable
        get() = stringResource( this.textId )
}