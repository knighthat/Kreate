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

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.ui.screen.player.LYRICS_WINDOW_HEIGHT
import me.knighthat.kreate3.ui.screen.player.LYRICS_WINDOW_WIDTH

/** What the buttons of the picture-in-picture window ask for. */
internal enum class PictureInPictureCommand(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
) {
    PREVIOUS(R.drawable.skip_previous, R.string.semantic_previous),
    PLAY(R.drawable.play_arrow, R.string.semantic_play),
    PAUSE(R.drawable.pause, R.string.semantic_pause),
    NEXT(R.drawable.skip_next, R.string.semantic_skip_next),
}

private const val ACTION = "me.knighthat.kreate3.PICTURE_IN_PICTURE_ACTION"
private const val EXTRA_COMMAND = "me.knighthat.kreate3.PICTURE_IN_PICTURE_COMMAND"

/**
 * The parameters of the picture-in-picture window: square for the cover, tall for the lyrics,
 * with the buttons to go back, play or pause and skip.
 *
 * @param isPlaying whether the song is playing, the middle button pauses it then
 * @param lyrics whether the window shows the lyrics page
 * @param autoEnter whether the window opens when the user leaves the app, Android 12 and up;
 * Android 11 opens it from `onUserLeaveHint`
 */
@RequiresApi(Build.VERSION_CODES.R)
internal fun Context.pictureInPictureParams(
    isPlaying: Boolean,
    lyrics: Boolean,
    autoEnter: Boolean,
): PictureInPictureParams {
    val middle = if (isPlaying) PictureInPictureCommand.PAUSE else PictureInPictureCommand.PLAY
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(
            if (lyrics) Rational(LYRICS_WINDOW_WIDTH, LYRICS_WINDOW_HEIGHT) else Rational(1, 1)
        )
        .setActions(
            listOf(PictureInPictureCommand.PREVIOUS, middle, PictureInPictureCommand.NEXT)
                .map { remoteAction(it) }
        )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        builder.setAutoEnterEnabled(autoEnter)
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.R)
private fun Context.remoteAction(command: PictureInPictureCommand): RemoteAction {
    val label = getString(command.label)
    val intent = PendingIntent.getBroadcast(
        this,
        command.ordinal,
        Intent(ACTION).setPackage(packageName).putExtra(EXTRA_COMMAND, command.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    // The window draws the icons as they are, over its dark controls
    val icon = Icon.createWithResource(this, command.icon).setTint(Color.WHITE)
    return RemoteAction(icon, label, label, intent)
}

/** The filter of [PictureInPictureReceiver]. */
internal fun pictureInPictureFilter() = IntentFilter(ACTION)

/** Hands the buttons of the picture-in-picture window to the player. */
internal class PictureInPictureReceiver(
    private val controller: PlaybackController,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra(EXTRA_COMMAND)?.let { name ->
            PictureInPictureCommand.entries.firstOrNull { it.name == name }
        }) {
            PictureInPictureCommand.PREVIOUS -> controller.previous()
            PictureInPictureCommand.PLAY,
            PictureInPictureCommand.PAUSE -> controller.togglePlay()

            PictureInPictureCommand.NEXT -> controller.next()
            null -> Unit
        }
    }
}
