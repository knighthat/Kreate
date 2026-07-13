package app.kreate.internal.innertube.models

import android.content.Context
import android.content.Intent
import app.kreate.gateway.innertube.YouTubeConstants
import app.kreate.gateway.innertube.models.InnertubeArtist


fun InnertubeArtist.share( context: Context ) {
    val url = "${YouTubeConstants.YOUTUBE_MUSIC_URL}/channel/$id"

    val intent = Intent().setAction( Intent.ACTION_SEND ).setType( "text/plain" ).putExtra( Intent.EXTRA_TEXT, url )
    val chooser = Intent.createChooser( intent, null )
    context.startActivity( chooser )
}