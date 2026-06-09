package app.kreate.android.themed.common.component.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.themed.common.component.settings.SettingComponents.Action
import app.kreate.preferences.Preferences
import app.kreate.util.scrollingText
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.TextInputDialog


class SettingInputDialogNew<V>(
    constraint: String,
    initValue: V,
    private val title: String,
    private val action: Action,
    override val keyboardOption: KeyboardOptions,
    private val onValueSet: (String) -> Unit
) : TextInputDialog(constraint) {

    override val dialogTitle: String
        @Composable
        get() = title

    override var value: TextFieldValue by mutableStateOf( TextFieldValue(initValue.toString()) )
    override var isActive: Boolean by mutableStateOf(false)

    override fun onSet( newValue: String ) {
        super.onSet(newValue)
        if( errorMessage.isNotBlank() )
            return

        onValueSet( newValue )

        hideDialog()
    }
}

@Composable
fun <K, V> SettingComponents.InputDialogEntry(
    preference: Preferences<K, V>,
    title: String,
    constraint: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    keyboardOption: KeyboardOptions = KeyboardOptions.Default,
    isEnabled: Boolean = true,
    action: Action = Action.NONE,
    trailingContent: @Composable () -> Unit = {},
    onValueChanged: (String) -> Unit = {}
) {
    val (colorPalette, typography) = LocalAppearance.current
    val value by preference.collectAsStateWithLifecycle()
    val dialog = remember( value ) {
        SettingInputDialogNew(constraint, value, title, action, keyboardOption, onValueChanged)
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