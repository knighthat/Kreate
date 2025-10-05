package it.fast4x.rimusic.utils

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.kreate.android.R
import app.kreate.database.models.Playlist
import app.kreate.util.MONTHLY_PREFIX
import it.fast4x.rimusic.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import me.knighthat.utils.TimeDateUtils
import java.time.LocalDate

private suspend fun addMonthlyPlaylist( from: LocalDate, to: LocalDate, playlistName: String ) =
    Database.eventTable
            .findSongsMostPlayedBetween(
                from = TimeDateUtils.toStartDateMillis( from ),
                to = TimeDateUtils.toStartDateMillis( to )
            )
            .first()
            .let {
                Database.asyncTransaction {
                    mapIgnore(
                        playlist = Playlist(name = playlistName),
                        songs = it.toTypedArray()
                    )
                }
            }

@Composable
fun CheckMonthlyPlaylist() {
    val (lastMonth, thisMonth) = remember {
        val firstOfThisMonth = LocalDate.now().withDayOfMonth( 1 )
        firstOfThisMonth.minusMonths( 1L ) to firstOfThisMonth
    }

    LaunchedEffect( lastMonth, thisMonth ) {
        // I.E. April 2025 returns "monthly:202504"
        val playlistName = "$MONTHLY_PREFIX${thisMonth.year}${thisMonth.monthValue}"

        Database.playlistTable
                .exists( playlistName )
                .flowOn( Dispatchers.IO )
                .collectLatest { isMonthlyPlaylistExist ->
                    // Force cancel this to prevent further updates
                    if( isMonthlyPlaylistExist ) return@collectLatest

                    addMonthlyPlaylist( lastMonth, thisMonth, playlistName )
                }
    }
}

@Composable
fun getTitleMonthlyPlaylist(playlist: String): String {

    val y = playlist.substring(0,4)
    val m = playlist.substring(5,7).toInt()
    return when (m) {
        1 -> stringResource(R.string.month_january_s).format(y)
        2 -> stringResource(R.string.month_february_s).format(y)
        3 -> stringResource(R.string.month_march_s).format(y)
        4 -> stringResource(R.string.month_april_s).format(y)
        5 -> stringResource(R.string.month_may_s).format(y)
        6 -> stringResource(R.string.month_june_s).format(y)
        7 -> stringResource(R.string.month_july_s).format(y)
        8 -> stringResource(R.string.month_august_s).format(y)
        9 -> stringResource(R.string.month_september_s).format(y)
        10 -> stringResource(R.string.month_october_s).format(y)
        11 -> stringResource(R.string.month_november_s).format(y)
        12 -> stringResource(R.string.month_december_s).format(y)
        else -> playlist
    }
}

fun getTitleMonthlyPlaylist( context: Context, playlist: String ): String {
    
    fun getMonthString( @DrawableRes monthId: Int, year: String ) =
        context.resources.getString( monthId ).format( year )

    val y = playlist.substring(0,4)
    val m = playlist.substring(5,7).toInt()
    return when( m ) {
        1 -> getMonthString( R.string.month_january_s, y )
        2 -> getMonthString( R.string.month_february_s, y )
        3 -> getMonthString( R.string.month_march_s, y )
        4 -> getMonthString( R.string.month_april_s, y )
        5 -> getMonthString( R.string.month_may_s, y )
        6 -> getMonthString( R.string.month_june_s, y )
        7 -> getMonthString( R.string.month_july_s, y )
        8 -> getMonthString( R.string.month_august_s, y )
        9 -> getMonthString( R.string.month_september_s, y )
        10 -> getMonthString( R.string.month_october_s, y )
        11 -> getMonthString( R.string.month_november_s, y )
        12 -> getMonthString( R.string.month_december_s, y )
        else -> playlist
    }
}


fun getTitleMonthlyPlaylistFromContext(playlist: String, context: Context): String {

    val y = playlist.substring(0,4)
    val m = playlist.substring(5,7).toInt()
    return when (m) {
        1 -> context.resources.getString(R.string.month_january_s).format(y)
        2 -> context.resources.getString(R.string.month_february_s).format(y)
        3 -> context.resources.getString(R.string.month_march_s).format(y)
        4 -> context.resources.getString(R.string.month_april_s).format(y)
        5 -> context.resources.getString(R.string.month_may_s).format(y)
        6 -> context.resources.getString(R.string.month_june_s).format(y)
        7 -> context.resources.getString(R.string.month_july_s).format(y)
        8 -> context.resources.getString(R.string.month_august_s).format(y)
        9 -> context.resources.getString(R.string.month_september_s).format(y)
        10 -> context.resources.getString(R.string.month_october_s).format(y)
        11 -> context.resources.getString(R.string.month_november_s).format(y)
        12 -> context.resources.getString(R.string.month_december_s).format(y)
        else -> playlist
    }
}