package it.fast4x.rimusic.enums

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import app.kreate.android.R
import app.kreate.component.Drawable
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandSearch
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandStartRadio
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleDownload
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleLike
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleRepeatMode
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleShuffle
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.cell_tower
import kreate.resources.generated.resources.download
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.repeat
import kreate.resources.generated.resources.search
import kreate.resources.generated.resources.shuffle
import me.knighthat.enums.TextView
import org.jetbrains.compose.resources.DrawableResource

enum class NotificationButtons(
    @field:StringRes override val androidTextId: Int,
    override val iconId: DrawableResource
): TextView, Drawable {

    Download(R.string.download, Res.drawable.download),

    Favorites(R.string.favorites, Res.drawable.favorite_filled),

    Repeat(R.string.repeat, Res.drawable.repeat),

    Shuffle(R.string.shuffle, Res.drawable.shuffle),

    Radio(R.string.start_radio, Res.drawable.cell_tower),

    Search(android.R.string.search_go, Res.drawable.search);

    val sessionCommand: SessionCommand
    get() = when (this) {
        Download -> CommandToggleDownload
        Favorites -> CommandToggleLike
        Repeat -> CommandToggleRepeatMode
        Shuffle -> CommandToggleShuffle
        Radio -> CommandStartRadio
        Search -> CommandSearch
    }

    val pendingIntent: PendingIntent
        @OptIn(UnstableApi::class)
        get() = when (this) {
            Download -> PlayerServiceModern.Action.download.pendingIntent
            Favorites -> PlayerServiceModern.Action.like.pendingIntent
            Repeat -> PlayerServiceModern.Action.repeat.pendingIntent
            Shuffle -> PlayerServiceModern.Action.shuffle.pendingIntent
            Radio -> PlayerServiceModern.Action.playradio.pendingIntent
            Search -> PlayerServiceModern.Action.search.pendingIntent
        }
}