package me.knighthat.component.dialog

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.medium

object AppearanceChangeDialog: ConfirmDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.title_appearance_changes)

    override var isActive: Boolean by mutableStateOf(false)

    override fun hideDialog() {
        isActive = false
    }

    override fun onConfirm() {
        isActive = false
    }

    @Composable
    override fun Buttons() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
        ) {
 
            BasicText(
                text = stringResource(R.string.confirm),
                style = typography().xs
                    .medium
                    .copy(
                        color = colorPalette().accent,
                        textAlign = TextAlign.Center
                    ),
                modifier = InteractiveDialog.ButtonModifier()
                    .fillMaxWidth(0.48f)
                    .border(
                        width = 2.dp,
                        color = colorPalette().accent,
                        shape = RoundedCornerShape(20)
                    )
                    .padding(vertical = 10.dp)
                    .clickable(onClick = ::onConfirm)
            )
        }
    }

    @Composable
    override fun DialogBody() {
        BasicText(
            text = stringResource(R.string.appearance_changes_message),
            style = typography().s.copy(
                color = colorPalette().text,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(vertical = 20.dp)
        )
    }
} 