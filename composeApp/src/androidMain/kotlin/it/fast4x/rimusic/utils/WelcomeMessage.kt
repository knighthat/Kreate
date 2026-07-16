package it.fast4x.rimusic.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.compose.R
import it.fast4x.rimusic.ui.components.themed.TitleMiniSection
import it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import it.fast4x.rimusic.ytAccountName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun WelcomeMessage(){
    val hour =
        remember {
            val date = Calendar.getInstance().time
            val formatter = SimpleDateFormat( "HH", Locale.getDefault() )
            formatter.format(date).toInt()
        }

    val message = when (hour) {
        in 6..12 -> {
            stringResource(R.string.good_morning)
        }

        in 13..17 -> {
            stringResource(R.string.good_afternoon)
        }

        in 18..23 -> {
            stringResource(R.string.good_evening)
        }

        else -> {
            stringResource(R.string.good_night)
        }
    }.let {
        if (isYouTubeLoggedIn()) "$it, ${ytAccountName()}"
        else it
    }

    TitleMiniSection(
        title = message,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}