package it.fast4x.rimusic.enums;

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.mini_player
import kreate.resources.generated.resources.word_both
import kreate.resources.generated.resources.word_disabled
import kreate.resources.generated.resources.word_player
import org.jetbrains.compose.resources.StringResource

enum class BackgroundProgress(
    override val textId: StringResource
): TextView {

    Player(Res.string.word_player),

    MiniPlayer(Res.string.mini_player),

    Both(Res.string.word_both),

    Disabled(Res.string.word_disabled);
}
