package it.fast4x.rimusic.enums

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.by_casual_played_song
import kreate.resources.generated.resources.by_last_played_song
import kreate.resources.generated.resources.by_most_played_song
import kreate.resources.generated.resources.history_2
import kreate.resources.generated.resources.shuffle
import kreate.resources.generated.resources.trending_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class PlayEventsType(
    override val textId: StringResource,
    override val iconId: DrawableResource
): TextView, Drawable {

    MostPlayed(Res.string.by_most_played_song, Res.drawable.trending_up),
    LastPlayed(Res.string.by_last_played_song, Res.drawable.history_2),
    CasualPlayed(Res.string.by_casual_played_song, Res.drawable.shuffle);
}