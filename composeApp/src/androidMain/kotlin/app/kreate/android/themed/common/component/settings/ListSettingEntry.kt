package app.kreate.android.themed.common.component.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import app.kreate.android.themed.common.component.settings.SettingComponents.Action
import app.kreate.util.scrollingText
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.Dialog
import me.knighthat.component.dialog.RestartAppDialog


class ListSelector<T>(
    val entries: Array<T>,
    val selectedState: State<T>,
    val title: String,
    val getName: @Composable (T) -> String,
    val onValueChanged: (T) -> Unit
) : Dialog {

    override val dialogTitle: String
        @Composable
        get() = title

    override var isActive: Boolean by mutableStateOf( false )

    @Composable
    override fun DialogBody() {
        val selected by selectedState
        val scrollState = rememberScrollState()
        val (colorPalette, typography) = LocalAppearance.current

        Column( Modifier.verticalScroll(scrollState) ) {
            entries.forEach { e ->
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
                            onValueChanged( e )
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun <V> SettingComponents.ListEntry(
    entries: Array<V>,
    selectedState: State<V>,
    title: String,
    getName: @Composable (V) -> String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    isEnabled: Boolean = true,
    action: Action = Action.NONE,
    trailingContent: @Composable () -> Unit = {},
    onValueChanged: (V) -> Unit = {}
) {
    val (colorPalette, typography) = LocalAppearance.current
    val dialog = remember(title) {
        ListSelector(entries, selectedState, title, getName) { newValue ->
            onValueChanged( newValue )

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