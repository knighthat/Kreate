package app.kreate.android.themed.common.component.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.themed.common.component.settings.SettingComponents.Action
import app.kreate.android.utils.scrollingText
import app.kreate.component.TextView
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.Dialog
import me.knighthat.component.dialog.RestartAppDialog


class Selector<E>(
    val preferences: Preferences.EnumPref<E>,
    val title: String,
    val action: Action,
    val onValueChanged: (E) -> Unit,
    val getName: @Composable (E) -> String
) : Dialog where E: Enum<E> {

    override val dialogTitle: String
        @Composable
        get() = title

    override var isActive: Boolean by mutableStateOf( false )

    @Composable
    override fun DialogBody() {
        val selected by preferences.collectAsStateWithLifecycle()
        val scrollState = rememberScrollState()
        val (colorPalette, typography) = LocalAppearance.current

        Column( Modifier.verticalScroll(scrollState) ) {
            preferences.entries().forEach { e ->
                ListItem(
                    headlineContent = {
                        val style = typography.s

                        BasicText(
                            text = getName( e ),
                            style = if( selected === e ) style.semiBold else style
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if( selected === e ) colorPalette.accent else Color.Transparent,
                        headlineColor = if( selected === e ) colorPalette.onAccent else colorPalette.text
                    ),
                    modifier = Modifier.clickable(
                        enabled = true,
                        onClick = {
                            hideDialog()

                            preferences.update( e )
                            onValueChanged( e )

                            if ( action == Action.RESTART_APP )
                                RestartAppDialog.showDialog()
                        }
                    )
                )
            }
        }
    }
}

@Composable
inline fun <reified E : Enum<E>> SettingComponents.EnumEntry(
    preference: Preferences.EnumPref<E>,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    isEnabled: Boolean = true,
    action: Action = Action.NONE,
    noinline getName: @Composable (E) -> String = {
      if( it is TextView ) it.text else it.name
    },
    noinline trailingContent: @Composable () -> Unit = {},
    noinline onValueChanged: (E) -> Unit = {}
) {
    val (colorPalette, typography) = LocalAppearance.current
    val dialog = remember(preference, title) {
        Selector(preference, title, action, onValueChanged, getName)
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
        trailingContent = trailingContent,
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
inline fun <reified E> SettingComponents.EnumEntry(
    preference: Preferences.EnumPref<E>,
    title: String,
    @StringRes subtitleId: Int,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    action: Action = Action.NONE,
    noinline getName: @Composable (E) -> String = { it.text },
    noinline trailingContent: @Composable () -> Unit = {},
    noinline onValueChanged: (E) -> Unit = {}
) where E : Enum<E>, E: TextView =
    EnumEntry( preference, title, modifier, stringResource(subtitleId), isEnabled, action, getName, trailingContent, onValueChanged )