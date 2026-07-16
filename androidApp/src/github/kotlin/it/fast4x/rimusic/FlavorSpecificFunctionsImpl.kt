package it.fast4x.rimusic

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import app.kreate.android.FlavorSpecificFunctions
import app.kreate.android.themed.common.component.settings.SettingEntrySearch


class FlavorSpecificFunctionsImpl : FlavorSpecificFunctions {

    @Composable
    override fun UpdateHandler() = me.knighthat.updater.UpdateHandler()

    override fun updateSection(
        scope: LazyListScope,
        search: SettingEntrySearch
    ) = app.kreate.android.themed.common.screens.settings.general.updateSection( scope, search )
}