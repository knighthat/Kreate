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

package me.knighthat.kreate.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity found in context")
}

/**
 * Permission needed to read the music on the device, it depends on the Android version.
 */
val AUDIO_PERMISSION: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Determine whether you have been granted a particular permission.
 *
 * @param permission The name of the permission being checked.
 *
 * @return Whether user allowed this specific [permission].
 */
fun Context.isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Gets whether you should show UI with rationale before requesting a permission.
 *
 * @param permission A permission your app wants to request.
 *
 * @return Whether you should show permission rationale UI.
 */
fun Context.shouldShowRequestPermissionRationale(permission: String): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(findActivity(), permission)

/**
 * @return Whether user has granted app permission to send notifications
 */
fun Context.hasNotificationPermission(): Boolean =
    // Before Android 13 notifications don't need a permission
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)

/**
 * @return Whether user has granted app permission to read music on the device
 */
fun Context.hasAudioPermission(): Boolean = isPermissionGranted(AUDIO_PERMISSION)