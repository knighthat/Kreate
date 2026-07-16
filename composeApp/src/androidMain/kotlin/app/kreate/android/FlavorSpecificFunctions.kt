package app.kreate.android

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import app.kreate.android.themed.common.component.settings.SettingEntrySearch


interface FlavorSpecificFunctions {

    @Composable
    fun UpdateHandler()

    fun updateSection( scope: LazyListScope, search: SettingEntrySearch )
}