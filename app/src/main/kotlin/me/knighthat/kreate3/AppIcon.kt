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

package me.knighthat.kreate3

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP

/**
 * The icons the app can show in the launcher, each an `activity-alias` of [KreateActivity] in
 * the manifest named [alias]. Only the chosen one is enabled.
 *
 * The system keeps which alias is enabled, not Preferences, so it's the same for every profile.
 */
enum class AppIcon(private val alias: String) {
    BLACK("me.knighthat.kreate3.BlackIcon"),
    GRADIENT("me.knighthat.kreate3.GradientIcon");

    private fun component(context: Context): ComponentName =
        ComponentName(context.packageName, alias)

    /**
     * Shows this icon in the launcher. Some launchers remove the app's home screen shortcuts.
     */
    fun apply(context: Context) {
        // Enabled before the others are disabled, so the app never drops out of the launcher
        setEnabled(context, true)
        entries.filter { it != this }.forEach { it.setEnabled(context, false) }
    }

    private fun setEnabled(context: Context, enabled: Boolean) =
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DISABLED,
            DONT_KILL_APP,
        )

    companion object {

        /** The icon shown in the launcher, [BLACK] until another is picked. */
        fun current(context: Context): AppIcon =
            entries.firstOrNull {
                context.packageManager.getComponentEnabledSetting(it.component(context)) ==
                        COMPONENT_ENABLED_STATE_ENABLED
            } ?: BLACK
    }
}
