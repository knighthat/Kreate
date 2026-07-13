package app.kreate.internal.innertube.models

import android.content.Context
import android.content.Intent
import app.kreate.gateway.innertube.YouTubeConstants
import app.kreate.gateway.innertube.models.InnertubePlaylist


fun InnertubePlaylist.share( context: Context ) {
    val id = id.removePrefix( "VL" )
    val url = "${YouTubeConstants.YOUTUBE_MUSIC_URL}/playlist?list=$id"

    val intent = Intent().setAction( Intent.ACTION_SEND ).setType( "text/plain" ).putExtra( Intent.EXTRA_TEXT, url )
    val chooser = Intent.createChooser( intent, null )
    context.startActivity( chooser )
}