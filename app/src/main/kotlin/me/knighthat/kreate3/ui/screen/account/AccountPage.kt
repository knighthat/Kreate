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

package me.knighthat.kreate3.ui.screen.account

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold

/**
 * A page of [AccountScreen] titled [title] that shows nothing yet.
 */
@Composable
fun AccountPage(@StringRes title: Int, onBack: () -> Unit, modifier: Modifier = Modifier) =
    DetailScaffold(stringResource(title), onBack, modifier) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

@ScreenPreviews
@Composable
private fun AccountPagePreview() = PreviewTheme {
    AccountPage(R.string.screen_title_history, onBack = {})
}
