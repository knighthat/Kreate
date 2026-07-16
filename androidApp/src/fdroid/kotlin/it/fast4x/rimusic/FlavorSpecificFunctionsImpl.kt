package it.fast4x.rimusic

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import app.kreate.android.FlavorSpecificFunctions
import app.kreate.android.themed.common.component.settings.SettingEntrySearch


class FlavorSpecificFunctionsImpl : FlavorSpecificFunctions {

    @Composable
    override fun UpdateHandler() { /* Does nothing */ }

    override fun updateSection(
        scope: LazyListScope,
        search: SettingEntrySearch
    ) { /* Does nothing */ }
}