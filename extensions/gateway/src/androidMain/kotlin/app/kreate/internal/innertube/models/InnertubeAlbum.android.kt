package app.kreate.internal.innertube.models

import android.content.Context
import android.content.Intent
import app.kreate.gateway.innertube.models.InnertubeAlbum


fun InnertubeAlbum.share( context: Context ) {
    val url = urlCanonical ?: return
    val intent = Intent().setAction( Intent.ACTION_SEND ).setType( "text/plain" ).putExtra( Intent.EXTRA_TEXT, url )
    val chooser = Intent.createChooser( intent, null )
    context.startActivity( chooser )
}