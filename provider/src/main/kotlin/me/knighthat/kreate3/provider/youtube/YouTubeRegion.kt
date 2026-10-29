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

package me.knighthat.kreate3.provider.youtube

import android.content.res.Resources
import me.knighthat.kreate3.preferences.Preferences

/**
 * Country codes YouTube offers in its location picker (`account/account_menu` of the website,
 * taken 2026-10-28), the choices of [Preferences.YOUTUBE_REGION].
 */
val YOUTUBE_REGIONS = listOf(
    "AE", "AM", "AR", "AT", "AU", "AZ", "BA", "BD", "BE", "BG", "BH", "BO", "BR", "BY", "CA", "CH",
    "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "DZ", "EC", "EE", "EG", "ES", "FI", "FR", "GB",
    "GE", "GH", "GR", "GT", "HK", "HN", "HR", "HU", "ID", "IE", "IL", "IN", "IQ", "IS", "IT", "JM",
    "JO", "JP", "KE", "KH", "KR", "KW", "KZ", "LA", "LB", "LI", "LK", "LT", "LU", "LV", "LY", "MA",
    "MD", "ME", "MK", "MT", "MX", "MY", "NG", "NI", "NL", "NO", "NP", "NZ", "OM", "PA", "PE", "PG",
    "PH", "PK", "PL", "PR", "PT", "PY", "QA", "RO", "RS", "RU", "SA", "SE", "SG", "SI", "SK", "SN",
    "SV", "TH", "TN", "TR", "TW", "TZ", "UA", "UG", "US", "UY", "VE", "VN", "YE", "ZA", "ZW",
)

/**
 * The country of the catalog YouTube answers from: [Preferences.YOUTUBE_REGION], or the region
 * of the device's own language settings while it is empty. The app's language doesn't change
 * it, the system's resources keep the device's locale. `US` when the device has no region.
 */
internal fun youTubeRegion(): String =
    Preferences.YOUTUBE_REGION.value.ifEmpty {
        Resources.getSystem().configuration.locales[0].country.ifEmpty { "US" }
    }
