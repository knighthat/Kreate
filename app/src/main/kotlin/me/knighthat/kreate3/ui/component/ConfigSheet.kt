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

package me.knighthat.kreate3.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R

/**
 * The options of a screen in a sheet as tall as they need: [actions] as a row of buttons, an icon
 * over its label ([LabeledIconButton]), then [options] under a divider, `null` for none.
 *
 * @param actions gets `close`, which slides the sheet away and then runs what it is given; an
 * action calls it, an option applies at once and leaves the sheet open
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSheet(
    onDismiss: () -> Unit,
    actions: @Composable RowScope.(close: (then: () -> Unit) -> Unit) -> Unit,
    options: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // Opens on its content at once, never half of it
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ConfigSheetContent(actions = { actions(::close) }, options = options)
    }
}

@Composable
private fun ConfigSheetContent(
    actions: @Composable RowScope.() -> Unit,
    options: (@Composable ColumnScope.() -> Unit)?,
) = Column(
    Modifier
        .verticalScroll(rememberScrollState())
        .padding(bottom = 16.dp)
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) { actions() }
    if (options != null) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        options()
    }
}

/**
 * An option of a [ConfigSheet] that is on or off, a tap on the row turns it around. Unless
 * [enabled], the row is grayed out and can't be tapped.
 */
@Composable
fun ConfigToggle(
    @DrawableRes icon: Int,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) = ListItem(
    headlineContent = { Text(label) },
    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
    // The row takes the tap, the switch only shows it
    trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier
        .toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = { onToggle() },
        )
        // The look of a disabled button
        .alpha(if (enabled) 1f else 0.38f),
)

//<editor-fold defaultstate="collapsed" desc="Previews">
/** What the sheet shows, without the sheet: a preview can't open one. */
@OptIn(ExperimentalMaterial3Api::class)
@ComponentPreviews
@Composable
private fun ConfigSheetPreview() = PreviewTheme {
    Surface(color = BottomSheetDefaults.ContainerColor) {
        Column(modifier = Modifier.width(360.dp)) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            ConfigSheetContent(
                actions = {
                    LabeledIconButton(R.drawable.play_arrow, "Play all") {}
                    LabeledIconButton(R.drawable.shuffle, "Shuffle") {}
                    LabeledIconButton(R.drawable.casino, "Random", enabled = false) {}
                },
                options = {
                    ConfigToggle(R.drawable.thumb_down, "Show disliked", checked = true) {}
                    ConfigToggle(R.drawable.cloud_done, "Cached only", checked = false) {}
                    ConfigToggle(R.drawable.date_range, "Off", checked = false, enabled = false) {}
                },
            )
        }
    }
}

/** A sheet of actions alone has no divider. */
@OptIn(ExperimentalMaterial3Api::class)
@ComponentPreviews
@Composable
private fun ConfigSheetActionsPreview() = PreviewTheme {
    Surface(color = BottomSheetDefaults.ContainerColor) {
        Column(modifier = Modifier.width(360.dp)) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            ConfigSheetContent(
                actions = { LabeledIconButton(R.drawable.casino, "Open random") {} },
                options = null,
            )
        }
    }
}
//</editor-fold>
