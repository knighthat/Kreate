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

package me.knighthat.kreate3.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.os.BundleCompat
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand

/**
 * The session command the app's controller adds songs after the current one with: the service
 * adds them to the player itself, which places them in the shuffled order too, see
 * [KreatePlayer.playNext]. A plain add at an index can't say where in the shuffled order it goes.
 */
@OptIn(UnstableApi::class)
internal object PlayNextCommand {

    private const val KEY_ITEMS = "items"

    val COMMAND = SessionCommand("me.knighthat.kreate3.PLAY_NEXT", Bundle.EMPTY)

    /** Arguments of [COMMAND] for [items], handed over like Media3 hands over a queue. */
    fun args(items: List<MediaItem>): Bundle = Bundle().apply {
        BundleCompat.putBinder(
            this,
            KEY_ITEMS,
            BundleListRetriever(items.map(MediaItem::toBundleIncludeLocalConfiguration))
        )
    }

    /** The items [args] carries, `null` when it carries none. */
    fun itemsOf(args: Bundle): List<MediaItem>? {
        val binder = BundleCompat.getBinder(args, KEY_ITEMS) ?: return null
        return BundleListRetriever.getList(binder).map(MediaItem::fromBundle)
    }
}
