package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.setting_description_update_checker_ask
import kreate.resources.generated.resources.setting_description_update_checker_automatic
import kreate.resources.generated.resources.setting_description_update_checker_disabled
import kreate.resources.generated.resources.setting_title_update_checker_ask
import kreate.resources.generated.resources.setting_title_update_checker_automatic
import kreate.resources.generated.resources.word_disabled
import org.jetbrains.compose.resources.StringResource

enum class CheckUpdateState(
    override val textId: StringResource,
    val subtitleId: StringResource
): TextView {

    DOWNLOAD_INSTALL(Res.string.setting_title_update_checker_automatic, Res.string.setting_description_update_checker_automatic),
    DISABLED(Res.string.word_disabled, Res.string.setting_description_update_checker_disabled),
    ASK(Res.string.setting_title_update_checker_ask, Res.string.setting_description_update_checker_ask);
}