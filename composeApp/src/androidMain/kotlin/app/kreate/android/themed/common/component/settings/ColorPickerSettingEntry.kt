package app.kreate.android.themed.common.component.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.themed.common.component.ColorPickerDialog
import app.kreate.android.themed.common.component.settings.SettingComponents.Action
import app.kreate.android.utils.scrollingText
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.RestartAppDialog


@Composable
fun SettingComponents.ColorPickerEntry(
    preference: Preferences.ColorPref,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    isEnabled: Boolean = true,
    action: Action = Action.NONE
) {
    val (colorPalette, typography) = LocalAppearance.current
    val selected by preference.collectAsStateWithLifecycle()
    val dialog = remember( selected ) {
        ColorPickerDialog(selected) { newValue ->
            preference.update( newValue )

            if ( action == Action.RESTART_APP )
                RestartAppDialog.showDialog()
        }
    }

    ListItem(
        headlineContent = {
            BasicText(
                text = title,
                maxLines = 1,
                style = typography.xs
                                  .semiBold
                                  .copy( colorPalette.text ),
                modifier = Modifier.scrollingText()
            )
        },
        supportingContent = {
            if( subtitle.isBlank() ) return@ListItem

            BasicText(
                text = subtitle,
                maxLines = 2,
                style = typography.xs
                                  .semiBold
                                  .copy( colorPalette.textSecondary )
            )
        },
        trailingContent = {
            Box(
                Modifier.size( 24.dp )
                        .background( dialog.color, RoundedCornerShape(8.dp) )
            )
        },
        colors = getColors( isEnabled ),
        modifier = modifier.clickable(
            enabled = isEnabled,
            role = Role.Button,
            onClick = dialog::showDialog
        )
    )

    dialog.Render()
}

@Composable
fun SettingComponents.ColorPickerEntry(
    preference: Preferences.ColorPref,
    title: String,
    @StringRes subtitleId: Int,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    action: Action = Action.NONE
) = ColorPickerEntry( preference, title, modifier, stringResource(subtitleId), isEnabled, action )