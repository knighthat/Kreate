/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold

/**
 * The frame of a settings page: a top bar named [title] with a back button, and [content]
 * stacked in a column that scrolls.
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DetailScaffold(title, onBack, modifier) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

/**
 * Names a group of settings.
 */
@Composable
fun SettingsSection(title: String) =
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )

/**
 * One row of a settings page: the [title], what it is set to or does as [summary], and [onClick]
 * for what a tap does. [trailing] sits at the end of the row.
 */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    )
}

/**
 * A setting that is on or off, a tap anywhere on the row flips it.
 */
@Composable
fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    SettingsItem(
        title = title,
        modifier = modifier,
        summary = summary,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

/**
 * The switch at the top of a page that turns on what the page sets up, set apart from the
 * settings under it, which the page hides while it is off.
 */
@Composable
fun MainSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (checked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/**
 * A setting with a few values to pick one from, all shown as radio buttons.
 */
@Composable
fun <T> RadioSetting(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup()) {
        options.forEach { option ->
            ListItem(
                headlineContent = { Text(label(option)) },
                leadingContent = { RadioButton(selected = option == value, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.selectable(
                    selected = option == value,
                    role = Role.RadioButton,
                    onClick = { onSelect(option) },
                ),
            )
        }
    }
}

/**
 * A setting with a few values to pick one from, in a dialog.
 */
@Composable
fun <T> ChoiceSetting(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    SettingsItem(title, modifier, summary = label(value), onClick = { open = true })

    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        ListItem(
                            headlineContent = { Text(label(option)) },
                            leadingContent = {
                                RadioButton(selected = option == value, onClick = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                onSelect(option)
                                open = false
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
}

/**
 * A setting typed in a dialog. [onSave] only gets text that [validate] accepted.
 *
 * @param summary shown under the title, defaults to the [value]
 * @param validate the reason the text is not acceptable, `null` when it is
 * @param hidden whether the text shows as dots, for passwords
 */
@Composable
fun TextSetting(
    title: String,
    value: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    summary: String = value,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    hidden: Boolean = false,
    validate: (String) -> String? = { null },
) {
    var open by remember { mutableStateOf(false) }

    SettingsItem(
        title = title,
        modifier = modifier,
        summary = summary.ifEmpty { "Not set" },
        onClick = { open = true },
    )

    if (open) {
        var text by remember { mutableStateOf(value) }
        val error = validate(text)

        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    placeholder = placeholder?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation =
                        if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = error == null,
                    onClick = {
                        onSave(text)
                        open = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/**
 * A whole number typed in a dialog, between [min] and [max].
 *
 * @param unit what the number counts, like "seconds", shown after it
 * @param note extra line under the title, like when a change applies
 */
@Composable
fun NumberSetting(
    title: String,
    value: Long,
    unit: String,
    min: Long,
    max: Long,
    onSave: (Long) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    TextSetting(
        title = title,
        value = value.toString(),
        summary = listOfNotNull("$value $unit", note).joinToString(". "),
        onSave = { onSave(it.toLong()) },
        modifier = modifier,
        keyboardType = KeyboardType.Number,
        validate = {
            val number = it.toLongOrNull()
            if (number == null || number !in min..max) "Between $min and $max" else null
        },
    )
}

/** `AUTOMATIC_DYNAMIC` as "Automatic dynamic". */
fun Enum<*>.prettyName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
