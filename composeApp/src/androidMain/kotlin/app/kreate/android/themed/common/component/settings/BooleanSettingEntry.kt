package app.kreate.android.themed.common.component.settings

import androidx.annotation.StringRes
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.themed.common.component.settings.SettingComponents.Action
import app.kreate.android.utils.scrollingText
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.drawCircle
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.RestartAppDialog


@Composable
private fun Switch(
    state: Preferences.BooleanPref,
    modifier: Modifier = Modifier,
    colorPalette: ColorPalette = LocalAppearance.current.colorPalette
) {
    val isChecked by state.collectAsStateWithLifecycle()
    val transition = updateTransition(targetState = isChecked, label = "state_transition")
    val background by transition.animateColor(label = "background_color") {
        if (it) colorPalette.accent else colorPalette.background1
    }
    val foreground by transition.animateColor(label = "foreground_color") {
        if (it) colorPalette.onAccent else colorPalette.textDisabled
    }
    val offset by transition.animateDp(label = "offset") {
        if (it) 36.dp else 12.dp
    }

    Canvas(
        modifier.size( width = 48.dp, height = 24.dp )
    ) {
        drawRoundRect(
            color = background,
            cornerRadius = CornerRadius(x = 12.dp.toPx(), y = 12.dp.toPx()),
        )

        drawCircle(
            color = foreground,
            radius = 8.dp.toPx(),
            center = size.center.copy( x = offset.toPx() ),
            shadow = Shadow(
                color = Color.Black.copy( alpha = if (transition.targetState) 0.4f else 0.1f ),
                blurRadius = 8.dp.toPx(),
                offset = Offset(
                    x = -1.dp.toPx(),
                    y = 1.dp.toPx()
                )
            )
        )
    }
}

@Composable
fun SettingComponents.BooleanEntry(
    preference: Preferences.BooleanPref,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    isEnabled: Boolean = true,
    action: Action = Action.NONE,
    onValueChanged: (Boolean) -> Unit = {}
) {
    val (colorPalette, typography) = LocalAppearance.current

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
            Switch(
                state = preference,
                modifier = Modifier.clickable(
                    enabled = isEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    // No effect
                    indication = null,
                    role = Role.Switch,
                    onClick = {
                        val flipped = preference.value.not()
                        preference.flip()

                        onValueChanged( flipped )

                        if ( action == Action.RESTART_APP )
                            RestartAppDialog.showDialog()
                    }
                )
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if( isEnabled ) colorPalette.text else colorPalette.textDisabled,
            supportingColor = if( isEnabled ) colorPalette.textSecondary else colorPalette.textDisabled
        ),
        modifier = modifier
    )
}

@Composable
fun SettingComponents.BooleanEntry(
    preference: Preferences.BooleanPref,
    title: String,
    @StringRes subtitleId: Int,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    action: Action = Action.NONE,
    onValueChanged: (Boolean) -> Unit = {}
) =
    BooleanEntry( preference, title, modifier, stringResource(subtitleId), isEnabled, action, onValueChanged )