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

package me.knighthat.kreate.ui.screen.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An artist of the OpenSubsonic server. It is fetched and stored first, then shows like
 * the artist in the library, as [content]. [title] names it while it loads.
 */
@Composable
fun OpenSubsonicArtistDetailScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: OpenSubsonicArtistDetailViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = SyncedDetailScreen(
    title = title,
    onBack = onBack,
    viewModel = viewModel,
    modifier = modifier,
    content = content,
)
