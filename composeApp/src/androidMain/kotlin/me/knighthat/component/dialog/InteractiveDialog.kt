package me.knighthat.component.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.medium

interface InteractiveDialog: Dialog {

    companion object {
        @JvmStatic
        fun ButtonModifier( extra: Modifier = Modifier ): Modifier =
            Modifier.wrapContentWidth(align = Alignment.CenterHorizontally)
                    .clip( RoundedCornerShape(20) )
                    .then( extra )

        @JvmStatic
        @Composable
        fun ConfirmButton(
            modifier: Modifier = ButtonModifier(),
            onConfirm: () -> Unit
        ) = BasicText(
            text = stringResource( R.string.confirm ),
            style = typography().xs
                                .medium
                                .copy(
                                    color = colorPalette().onAccent,
                                    textAlign = TextAlign.Center
                                ),
            modifier = modifier.clickable( onClick = onConfirm )
        )

        @JvmStatic
        @Composable
        fun CancelButton(
            modifier: Modifier = ButtonModifier(),
            onCancel: () -> Unit
        ) = BasicText(
            text = stringResource( android.R.string.cancel ),
            style = typography().xs
                                .medium
                                .copy(
                                    color = Color(android.graphics.Color.RED).copy( alpha = .3f ),
                                    textAlign = TextAlign.Center
                                ),
            modifier = modifier.clickable( onClick = onCancel )
        )
    }

    @Composable
    fun Buttons()
}