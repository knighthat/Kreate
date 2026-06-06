package it.fast4x.rimusic.enums

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import app.kreate.component.Drawable
import app.kreate.component.TextView
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandSearch
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandStartRadio
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleDownload
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleLike
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleRepeatMode
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleShuffle
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.action_change_repeat
import kreate.resources.generated.resources.action_download
import kreate.resources.generated.resources.action_like_dislike
import kreate.resources.generated.resources.action_search
import kreate.resources.generated.resources.action_start_radio
import kreate.resources.generated.resources.action_toggle_shuffle
import kreate.resources.generated.resources.cell_tower
import kreate.resources.generated.resources.download
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.repeat
import kreate.resources.generated.resources.search
import kreate.resources.generated.resources.shuffle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class NotificationButtons(
    override val textId: StringResource,
    override val iconId: DrawableResource
): TextView, Drawable {

    Download(Res.string.action_download, Res.drawable.download),

    Favorites(Res.string.action_like_dislike, Res.drawable.favorite_filled),

    Repeat(Res.string.action_change_repeat, Res.drawable.repeat),

    Shuffle(Res.string.action_toggle_shuffle, Res.drawable.shuffle),

    Radio(Res.string.action_start_radio, Res.drawable.cell_tower),

    Search(Res.string.action_search, Res.drawable.search);

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