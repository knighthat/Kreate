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

package me.knighthat.kreate3.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/*
 * Android version checks by the version people know instead of the API level or code name.
 * `@ChecksSdkIntAtLeast` lets lint's NewApi check treat them like `Build.VERSION.SDK_INT >= …`,
 * so calls inside `if (IS_ANDROID_10_AND_UP)` need no `@RequiresApi`. Below a version is
 * `!IS_ANDROID_13_AND_UP`. minSdk is 24 (Android 7), so nothing older needs a check.
 */

/** Android 8 (API 26, `O`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
val IS_ANDROID_8_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

/** Android 9 (API 28, `P`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
val IS_ANDROID_9_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/** Android 10 (API 29, `Q`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
val IS_ANDROID_10_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/** Android 11 (API 30, `R`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
val IS_ANDROID_11_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

/** Android 12 (API 31, `S`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
val IS_ANDROID_12_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Android 13 (API 33, `TIRAMISU`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
val IS_ANDROID_13_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Android 14 (API 34, `UPSIDE_DOWN_CAKE`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
val IS_ANDROID_14_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

/** Android 15 (API 35, `VANILLA_ICE_CREAM`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
val IS_ANDROID_15_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

/** Android 16 (API 36, `BAKLAVA`) and later. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
val IS_ANDROID_16_AND_UP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
