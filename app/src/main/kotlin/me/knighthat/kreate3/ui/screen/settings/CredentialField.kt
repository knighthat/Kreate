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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.Tooltip

/**
 * A field of one line for a credential. With [hidden] it shows as dots, and an eye button at its
 * end shows or hides the text. While not [enabled] it is dimmed and can't be typed in.
 *
 * @param placeholder shown while the field is empty and focused, none when `null`
 */
@Composable
internal fun CredentialField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hidden: Boolean,
    imeAction: ImeAction,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        singleLine = true,
        trailingIcon = if (hidden) {
            {
                val description = stringResource(
                    if (revealed) R.string.semantic_hide_text else R.string.semantic_show_text
                )
                Tooltip(description) {
                    IconButton(onClick = { revealed = !revealed }, enabled = enabled) {
                        Icon(
                            painterResource(
                                if (revealed) R.drawable.visibility_off else R.drawable.visibility
                            ),
                            contentDescription = description,
                        )
                    }
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (hidden) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        visualTransformation =
            if (hidden && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
    )
}
