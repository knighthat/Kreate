package app.kreate.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.kreate.component.TextView
import app.kreate.components.settings.SettingComponents.Action
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.ui.styling.LocalAppearance
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.semantic_open_selector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


@Composable
fun <T> SettingComponents.ListEntry(
    entries: Array<T>,
    title: String,
    getName: @Composable (T) -> String,
    selected: T,
    onConfirmRequest: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
    action: Action = Action.NONE,
    properties: ListDialogProperties = ListDialogProperties.default,
    trailingContent: @Composable () -> Unit = {}
) {
    var isDialogVisible by rememberSaveable { mutableStateOf(false) }
    var selected by remember( selected ) { mutableStateOf(selected) }

    Entry(
        title = title,
        subtitle = subtitle ?: getName( selected ),
        modifier = modifier,
        onClick = { isDialogVisible = true },
        trailingContent = trailingContent
    )

    if( isDialogVisible )
        AlertDialog(
            onDismissRequest = { isDialogVisible = false },
            shape = properties.shape,
            containerColor = properties.containerColor,
            iconContentColor = properties.iconContentColor,
            titleContentColor = properties.titleContentColor,
            textContentColor = Color.Transparent,
            tonalElevation = properties.tonalElevation,
            properties = properties.properties,
            confirmButton = {
                ConfirmButton {
                    isDialogVisible = false
                    onConfirmRequest( selected )
                }
            },
            dismissButton = {
                CancelButton { isDialogVisible = false }
            },
            icon = {
                if( icon == null ) return@AlertDialog

                Icon(
                    painter = painterResource( icon ),
                    // Not clickable
                    contentDescription = null
                )
            },
            title = {
                Text(title)
            },
            text = {
                LazyColumn(
                    Modifier.heightIn( max = 250.dp )
                ) {
                    items( entries ) { item ->
                        val isSelected = selected == item

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = getName( item ),
                                    fontWeight = if( isSelected ) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if( isSelected ) properties.selectedEntryBackground else properties.entryBackground,
                                headlineColor = if( isSelected ) properties.selectedEntryForeground else properties.entryForeground
                            ),
                            modifier = Modifier.clickable(
                                enabled = enabled,
                                onClickLabel = stringResource( Res.string.semantic_open_selector ),
                                role = Role.RadioButton,
                                onClick = {
                                    // To prevent accidental selections while scrolling,
                                    // changes are stored in memory and only applied
                                    // once the user explicitly confirms the action.
                                    selected = item
                                }
                            )
                        )
                    }
                }
            },
            modifier = Modifier.widthIn( 300.dp, 350.dp )
        )
}

@Composable
inline fun <reified E : Enum<E>> SettingComponents.EnumEntry(
    preference: Preferences.EnumPref<E>,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    action: Action = Action.NONE,
    noinline getName: @Composable (E) -> String = {
        if( it is TextView ) it.text else it.name
    },
    noinline trailingContent: @Composable () -> Unit = {},
    noinline onValueChanged: (E) -> Unit = {}
) =
    ListEntry(
        entries = enumValues(),
        title = title,
        getName = getName,
        selected = preference.value,
        onConfirmRequest = {
            preference.update( it )
            onValueChanged( it )
        },
        modifier = modifier,
        subtitle = subtitle,
        enabled = enabled,
        action = action,
        trailingContent = trailingContent
    )

@Immutable
data class ListDialogProperties(
    val shape: Shape,
    val containerColor: Color,
    val iconContentColor: Color,
    val titleContentColor: Color,
    val entryBackground: Color,
    val selectedEntryBackground: Color,
    val entryForeground: Color,
    val selectedEntryForeground: Color,
    val tonalElevation: Dp,
    val properties: DialogProperties
) {

    companion object {

        val default: ListDialogProperties
            @Composable
            get() {
                val colorPalette = LocalAppearance.current.colorPalette

                return ListDialogProperties(
                    shape = AlertDialogDefaults.shape,
                    containerColor = colorPalette.background0,
                    iconContentColor = colorPalette.text,
                    titleContentColor = colorPalette.text,
                    entryBackground = Color.Transparent,
                    selectedEntryBackground = colorPalette.accent,
                    entryForeground = colorPalette.text,
                    selectedEntryForeground = colorPalette.onAccent,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    properties = DialogProperties()
                )
            }
    }
}