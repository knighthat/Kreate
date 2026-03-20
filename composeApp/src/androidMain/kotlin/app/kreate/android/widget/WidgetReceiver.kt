package app.kreate.android.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


sealed class WidgetReceiver : GlanceAppWidgetReceiver() {

    companion object {

        const val KEY_IS_PLAYING = "app.kreate.android.widget.IS_PLAYING"
        const val KEY_METADATA = "app.kreate.android.widget.METADATA"
        const val ACTION_UPDATE = "app.kreate.android.widget.UPDATE"        // Don't forget manifest's <intent-filter>
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @OptIn(UnstableApi::class)
    override fun onReceive( context: Context, intent: Intent ) {
        super.onReceive(context, intent)

        if( intent.action == ACTION_UPDATE ) {
            val extras = intent.extras ?: return

            coroutineScope.launch {
                val isPlaying = extras.getBoolean(KEY_IS_PLAYING)
                val bundle = extras.getBundle(KEY_METADATA)
                val metadata = bundle?.let( MediaMetadata::fromBundle )

                (glanceAppWidget as Widget).update(context, isPlaying, metadata)
            }
        }
    }

    override fun onDeleted( context: Context, appWidgetIds: IntArray ) {
        coroutineScope.cancel()
        super.onDeleted(context, appWidgetIds)
    }
}