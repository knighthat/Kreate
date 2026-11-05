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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.AppRestart
import me.knighthat.kreate3.KreateApplication
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.CountSlider
import me.knighthat.kreate3.ui.component.LufsSlider
import me.knighthat.kreate3.ui.component.PercentSlider
import me.knighthat.kreate3.ui.component.RestartAppButton
import me.knighthat.kreate3.ui.component.SecondSlider
import me.knighthat.kreate3.ui.component.StopSlider
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold

/**
 * The frame of a settings page: a top bar named [title] with a back button, [actions] and, with
 * [restartApp], a button that restarts the app once confirmed, on while a setting needs it (see
 * [AppRestart]), and [content] stacked in a column that scrolls.
 *
 * @param restartApp whether the page has settings read with [AppRestart.readOnce]
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    restartApp: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val restartNeeded by AppRestart.needed.collectAsStateWithLifecycle(false)
    var confirmRestart by remember { mutableStateOf(false) }
    if (confirmRestart)
        RestartAppDialog(
            onRestart = {
                confirmRestart = false
                (context.applicationContext as KreateApplication).restartAfterPlayback()
            },
            onDismiss = { confirmRestart = false },
        )

    DetailScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        actions = {
            actions()
            if (restartApp) RestartAppButton({ confirmRestart = true }, enabled = restartNeeded)
        },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

/**
 * Asks whether to restart the app.
 */
@Composable
private fun RestartAppDialog(onRestart: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_restart_the_app)) },
        text = { Text(stringResource(R.string.dialog_body_restart_the_app)) },
        confirmButton = { TextButton(onClick = onRestart) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

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
 * for what a tap does. [trailing] sits at the end of the row. The title keeps to one line and the
 * summary to two, see [SettingsSummary].
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
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = summary?.let { { SettingsSummary(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    )
}

/**
 * A setting that is on or off, a tap anywhere on the row flips it. While not [enabled] the row is
 * dimmed and taps do nothing, the setting keeps its value.
 */
@Composable
fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SettingsItem(
        title = title,
        modifier = if (enabled) modifier else modifier.alpha(DISABLED_ALPHA),
        summary = summary,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

/**
 * A setting that is on or off with a page of its own: the switch at the end, past a divider, flips
 * it, and a tap on the texts calls [onClick] to open the page, only while it is [checked] unless
 * [textEnabled] says otherwise.
 */
@Composable
fun SwitchPageSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    textEnabled: Boolean = checked,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min),
) {
    SettingsItem(
        title = title,
        summary = summary,
        onClick = if (textEnabled) onClick else null,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
    )
    VerticalDivider(Modifier.height(SWITCH_DIVIDER_HEIGHT))
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Height of the line between the texts and the switch of a [SwitchPageSetting]. */
private val SWITCH_DIVIDER_HEIGHT = 32.dp

/**
 * What a settings row says under its title, cut to two lines. A note after a line break (who may
 * change it, …) gets two lines of its own.
 */
@Composable
internal fun SettingsSummary(summary: String) =
    Column {
        summary.lines().forEach { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }

/** How opaque a disabled row is, Material's disabled content. */
internal const val DISABLED_ALPHA = .38f

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
            if (checked)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = summary?.let { { SettingsSummary(it) } },
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
 *
 * @param summary shown under the title, defaults to the [label] of the [value]
 * @param enabled while not, the row is dimmed and taps do nothing
 */
@Composable
fun <T> ChoiceSetting(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String = label(value),
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }

    SettingsItem(
        title,
        if (enabled) modifier else modifier.alpha(DISABLED_ALPHA),
        summary = summary,
        onClick = if (enabled) ({ open = true }) else null,
    )

    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                // Long lists (languages) scroll inside the dialog
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
            confirmButton = {
                TextButton(onClick = {
                    open = false
                }) { Text(stringResource(R.string.action_close)) }
            },
        )
}

/**
 * A setting with a few values in order, picked by sliding a [StopSlider] to one, with
 * [leadingIcon] and [trailingIcon] at its ends. The row shows the [title] and the [label] of the
 * value under it, which follows the slider while it is dragged (its ball shows nothing); the
 * setting changes when it is let go.
 *
 * @param value shown as the first of [options] when it isn't one of them
 */
@Composable
fun <T> SliderSetting(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val index = options.indexOf(value).coerceAtLeast(0)
    // Where the slider is while it is dragged
    var position by remember(index, options.size) { mutableIntStateOf(index) }

    SliderRow(title, label(options[position.coerceIn(options.indices)]), true, modifier) {
        StopSlider(
            value = position,
            onValueChange = { position = it },
            stops = options.size,
            onValueChangeFinished = {
                // A tap moves and lets go in one frame, read the position it left
                val picked = options[position.coerceIn(options.indices)]
                if (picked != value) onSelect(picked)
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

/**
 * A setting of whole seconds in [range], picked on a [SecondSlider] whose ball shows the seconds.
 * The setting changes only when the slider is let go. While not [enabled] the row is dimmed and
 * the slider doesn't move.
 *
 * @param summary under the title, like why it's off; none when `null`
 * @param number what the ball shows for a second, the second itself by default
 */
@Composable
fun SecondSetting(
    title: String,
    value: Int,
    range: IntRange,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    number: (Int) -> String = Int::toString,
) {
    // Where the slider is while it is dragged
    var position by remember(value) { mutableIntStateOf(value.coerceIn(range)) }

    SliderRow(title, summary, enabled, modifier) {
        SecondSlider(
            value = position,
            onValueChange = { position = it },
            range = range,
            enabled = enabled,
            // A tap moves and lets go in one frame, read the position it left
            onValueChangeFinished = { if (position != value) onSelect(position) },
            number = number,
        )
    }
}

/**
 * A setting of a count in [range], picked on a [CountSlider] whose ball shows the count and whose
 * track has [unit] of it under. The setting changes only when the slider is let go. While not
 * [enabled] the row is dimmed and the slider doesn't move.
 *
 * @param summary under the title, like why it's off; none when `null`
 */
@Composable
fun CountSetting(
    title: String,
    value: Int,
    range: IntRange,
    unit: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    // Where the slider is while it is dragged
    var position by remember(value) { mutableIntStateOf(value.coerceIn(range)) }

    SliderRow(title, summary, enabled, modifier) {
        CountSlider(
            value = position,
            onValueChange = { position = it },
            range = range,
            unit = unit,
            enabled = enabled,
            // A tap moves and lets go in one frame, read the position it left
            onValueChangeFinished = { if (position != value) onSelect(position) },
        )
    }
}

/**
 * A setting of a whole percentage in [range], picked on a [PercentSlider] whose ball shows it, with
 * [leadingIcon] and [trailingIcon] at the slider's ends. The setting changes only when the slider
 * is let go. While not [enabled] the row is dimmed and the slider doesn't move.
 *
 * @param summary under the title, like why it's off; none when `null`
 */
@Composable
fun PercentSetting(
    title: String,
    value: Int,
    range: IntRange,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    // Where the slider is while it is dragged
    var position by remember(value) { mutableIntStateOf(value.coerceIn(range)) }

    SliderRow(title, summary, enabled, modifier) {
        PercentSlider(
            value = position,
            onValueChange = { position = it },
            range = range,
            enabled = enabled,
            // A tap moves and lets go in one frame, read the position it left
            onValueChangeFinished = { if (position != value) onSelect(position) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

/**
 * A setting of a loudness in LUFS, picked on a [LufsSlider] whose ball shows the value. The
 * setting changes only when the slider is let go. While not [enabled] the row is dimmed and the
 * slider doesn't move.
 *
 * @param summary under the title, like why it's off; none when `null`
 */
@Composable
fun LufsSetting(
    title: String,
    value: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    // Where the slider is while it is dragged
    var position by remember(value) { mutableFloatStateOf(value) }

    SliderRow(title, summary, enabled, modifier) {
        LufsSlider(
            value = position,
            onValueChange = { position = it },
            enabled = enabled,
            // A tap moves and lets go in one frame, read the position it left
            onValueChangeFinished = { if (position != value) onSelect(position) },
        )
    }
}

/**
 * A settings row with a [slider] under the [title] and [summary]. While not [enabled] the texts
 * are dimmed; the slider dims itself.
 */
@Composable
private fun SliderRow(
    title: String,
    summary: String?,
    enabled: Boolean,
    modifier: Modifier,
    slider: @Composable () -> Unit,
) = ListItem(
    headlineContent = {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
        )
    },
    supportingContent = {
        Column {
            summary?.let {
                Box(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)) { SettingsSummary(it) }
            }
            slider()
        }
    },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = modifier,
)

/**
 * A setting typed in a dialog. [onSave] only gets text that [validate] accepted.
 *
 * @param summary shown under the title, defaults to the [value]
 * @param validate the reason the text is not acceptable, `null` when it is
 * @param hidden whether the text shows as dots, for passwords
 * @param enabled while not, the row is dimmed and taps do nothing
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
    enabled: Boolean = true,
    validate: (String) -> String? = { null },
) {
    var open by remember { mutableStateOf(false) }

    SettingsItem(
        title = title,
        modifier = if (enabled) modifier else modifier.alpha(DISABLED_ALPHA),
        summary = summary.ifEmpty { stringResource(R.string.status_not_set) },
        onClick = if (enabled) ({ open = true }) else null,
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
                    placeholder = placeholder?.let {
                        {
                            Text(
                                it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
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
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    open = false
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * A whole number typed in a dialog, between [min] and [max].
 *
 * @param note extra line under the title, like when a change applies
 * @param enabled while not, the row is dimmed and taps do nothing
 */
@Composable
fun NumberSetting(
    title: String,
    value: Long,
    min: Long,
    max: Long,
    onSave: (Long) -> Unit,
    modifier: Modifier = Modifier,
    note: @Composable (Long) -> String? = { null },
    enabled: Boolean = true,
) {
    val outOfRange = stringResource(R.string.setting_description_between, min, max)

    TextSetting(
        title = title,
        value = value.toString(),
        summary = note(value).orEmpty(),
        onSave = { onSave(it.toLong()) },
        modifier = modifier,
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        validate = {
            val number = it.toLongOrNull()
            if (number == null || number !in min..max) outOfRange else null
        },
    )
}

