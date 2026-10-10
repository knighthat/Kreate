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

package me.knighthat.kreate.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.knighthat.kreate.constant.ContrastLevel
import me.knighthat.kreate.constant.ThemeMode
import me.knighthat.kreate.preferences.Preferences
import me.knighthat.kreate.preferences.collectAsStateWithLifecycle

private fun parseHexColor(hex: String): Color {
    val digits = hex.removePrefix("#")
    if (digits.length != 6) return Color.Unspecified

    val rgb = digits.toIntOrNull(16) ?: return Color.Unspecified
    return Color(0xFF000000.toInt() or rgb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropBox(
    label: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun KreateThemePreview() {
    val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()
    val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()

    var seedHex by rememberSaveable { mutableStateOf("") }

    KreateTheme(themeMode, contrastLevel, parseHexColor(seedHex)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EnumDropBox(
                    "Contrast level",
                    contrastLevel,
                    ContrastLevel.entries,
                    Preferences.CONTRAST_LEVEL::update
                )
                EnumDropBox(
                    "Theme mode",
                    themeMode,
                    ThemeMode.entries,
                    Preferences.THEME_MODE::update
                )

                if (themeMode == ThemeMode.THUMBNAIL)
                    OutlinedTextField(
                        value = seedHex,
                        onValueChange = { seedHex = it.take(7) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seed color (hex)") },
                        placeholder = { Text("#6750A4") },
                        isError = seedHex.isNotEmpty() && parseHexColor(seedHex).isUnspecified,
                        singleLine = true,
                    )


                HorizontalDivider()

                Text("Headline", style = MaterialTheme.typography.headlineMedium)
                Text("Title", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Body text sample, 3:45 / 12:08",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Label", style = MaterialTheme.typography.labelMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text("Filled") }
                    ElevatedButton(onClick = {}) { Text("Elevated") }
                    OutlinedButton(onClick = {}) { Text("Outlined") }
                    TextButton(onClick = {}) { Text("Text") }
                }

                var chipSelected by remember { mutableStateOf(true) }
                FilterChip(
                    selected = chipSelected,
                    onClick = { chipSelected = !chipSelected },
                    label = { Text("Filter chip") },
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var checked by remember { mutableStateOf(true) }
                    var switched by remember { mutableStateOf(true) }
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Switch(checked = switched, onCheckedChange = { switched = it })
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }

                var slider by remember { mutableStateOf(0.4f) }
                Slider(value = slider, onValueChange = { slider = it })
                LinearProgressIndicator(
                    progress = { slider },
                    modifier = Modifier.fillMaxWidth()
                )

                var text by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Card title", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Card content on surface container",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}