package me.knighthat.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.constant.TextView

interface TextView: TextView {

    @get:StringRes
    val androidTextId: Int
        get() = throw NotImplementedError("""
                This setting uses [${this::class.simpleName}#text] directly 
                or its [${this::class.simpleName}#textId] hasn't initialized!
        """.trimIndent())

    override val text: String
        @Composable
        get() = stringResource( this.androidTextId )
}