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

package me.knighthat.kreate3.patreon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.api.PatreonClaims
import me.knighthat.kreate3.api.PatreonFeature
import me.knighthat.kreate3.api.PatreonProfile
import me.knighthat.kreate3.preferences.Secrets

/**
 * Who is signed in to Patreon on this device, read once from the device secrets.
 *
 * @property claims what the token says, which decides what is unlocked ([PatreonClaims.has])
 * @property profile who it is and their membership, to show only; `null` when the API didn't say
 */
data class PatreonMember(val claims: PatreonClaims, val profile: PatreonProfile?) {

    override fun toString(): String = "PatreonMember($claims)"
}

/**
 * Patreon's information while the app runs: the member [PatreonAccount] signed in, decoded from
 * [Secrets.PATREON_TOKEN] and [Secrets.PATREON_PROFILE] once per change instead of by every reader.
 * Follows sign ins, sign outs and refreshes, which only write the secrets. Created as the app
 * starts (`platformModule`).
 *
 * What a tier unlocks is read with [has], from the token's `features`, never from the tier
 * number or the profile. The API still decides: this only adjusts the UI.
 */
class PatreonSession internal constructor(scope: CoroutineScope) {

    /** Who is signed in, `null` while signed out and until the secrets are read. */
    val member: StateFlow<PatreonMember?> =
        combine(Secrets.PATREON_TOKEN.state, Secrets.PATREON_PROFILE.state) { token, profile ->
            if (token.isEmpty())
                null
            else
                PatreonMember(PatreonClaims.of(token), PatreonProfile.decode(profile))
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Whether the signed in tier unlocks [feature], one of [PatreonFeature]; `false` signed out. */
    fun has(feature: String): Boolean = member.value?.claims?.has(feature) == true
}
