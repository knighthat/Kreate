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

package me.knighthat.kreate.ui.component

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import me.knighthat.kreate.ProfilesActivity
import me.knighthat.kreate.R
import me.knighthat.kreate.preferences.Preferences
import org.koin.core.context.GlobalContext

/**
 * An icon button whose only content is [icon], described to screen readers as [description].
 */
@Composable
private fun IconAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.arrow_back, "Back", onClick, modifier)

@Composable
fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.search, "Search", onClick, modifier)

@Composable
fun ProfileButton(modifier: Modifier = Modifier) =
    IconAction(
        icon = R.drawable.person,
        description = "Profile",
        onClick = {
            val context = GlobalContext.get().get<Context>()

            context.startActivity(
                ProfilesActivity.intent(
                    context,
                    Preferences.THEME_MODE.value,
                    Preferences.CONTRAST_LEVEL.value
                )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        modifier = modifier
    )

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.settings, "Settings", onClick, modifier)

@Composable
fun ConfigButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.tune, "Config", onClick, modifier)
