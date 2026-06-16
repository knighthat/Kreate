package app.kreate.android.themed.common.component.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kreate.components.settings.SettingComponents


fun LazyListScope.header(
    title: @Composable () -> String,
    modifier: Modifier = Modifier,
    subtitle: @Composable () -> String? = { null }
) = stickyHeader {
    SettingComponents.Header(
        title = title(),
        modifier = modifier,
        subtitle = subtitle()
    )
}

fun LazyListScope.header(
    @StringRes titleId: Int,
    modifier: Modifier = Modifier,
    subtitle: @Composable () -> String? = { null }
) = header( { stringResource( titleId ) }, modifier, subtitle )
