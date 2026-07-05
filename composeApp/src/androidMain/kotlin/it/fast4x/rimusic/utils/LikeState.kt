package it.fast4x.rimusic.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.database.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun getLikeState(mediaId: String): DrawableResource {
    val songLikeState by remember( mediaId ) {
        Database.songTable
                .likeState( mediaId )
                .distinctUntilChanged()
    }.collectAsState( false, Dispatchers.IO )
    val icon by app.kreate.preferences.Preferences.LIKE_ICON.collectAsStateWithLifecycle()

    return when( songLikeState ) {
        false -> icon.dislikeIconId
        null -> icon.neutralIconId
        true -> icon.likedIconId
    }
}

fun setLikeState(likedAt: Long?): Long? {
    val current =
     when (likedAt) {
        -1L -> null
        null -> System.currentTimeMillis()
        else -> -1L
    }
    //println("mediaItem setLikeState: $current")
    return current

}

