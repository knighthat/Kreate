package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class CheckUpdateState(
    @field:StringRes override val androidTextId: Int,
    @field:StringRes val subtitleId: Int
): TextView {

    DOWNLOAD_INSTALL( R.string.setting_title_update_checker_automatic, R.string.setting_description_update_checker_automatic ),
    DISABLED( R.string.vt_disabled, R.string.setting_description_update_checker_disabled ),
    ASK( R.string.ask, R.string.setting_description_update_checker_ask );
}