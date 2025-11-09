package app.kreate.component

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource


interface TextView {

    val textId: StringResource
        get() = throw NotImplementedError("""
                This setting uses [${this::class.simpleName}#text] directly 
                or its [${this::class.simpleName}#textId] hasn't initialized!
        """.trimIndent())

    val text: String
        @Composable
        get() = stringResource( textId )
}