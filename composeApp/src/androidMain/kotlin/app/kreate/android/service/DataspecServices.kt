package app.kreate.android.service

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import app.kreate.android.network.innertube.Store
import app.kreate.android.utils.CharUtils
import com.google.gson.Gson
import com.grack.nanojson.JsonObject
import io.ktor.client.statement.bodyAsText
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.Innertube.createPoTokenChallenge
import it.fast4x.innertube.models.PlayerResponse
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.nextPage
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.isConnectionMeteredEnabled
import it.fast4x.rimusic.models.Format
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.utils.isConnectionMetered
import it.fast4x.rimusic.utils.okHttpDataSourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper

private const val CHUNK_LENGTH = 512 * 1024L     // 512Kb

//<editor-fold defaultstate="collapsed" desc="Extractors">
private val jsonParser =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        useArrayPolymorphism = true
        explicitNulls = false
    }

/**
 * Store id of song just added to the database by [getFormatUrl].
 * This is created to reduce load to Room
 */
private lateinit var justAdded: String

@UnstableApi
private fun checkPlayability( playabilityStatus: PlayerResponse.PlayabilityStatus? ) {
    if( playabilityStatus?.status != "OK" )
        when( playabilityStatus?.status ) {
            "LOGIN_REQUIRED"    -> throw LoginRequiredException()
            "UNPLAYABLE"        -> throw UnplayableException()
            else                -> throw UnknownException()
        }
}

private fun extractFormat(
    streamingData: PlayerResponse.StreamingData?,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): PlayerResponse.StreamingData.Format? =
    when (audioQualityFormat) {
        AudioQualityFormat.High -> streamingData?.highestQualityFormat
        AudioQualityFormat.Medium -> streamingData?.mediumQualityFormat
        AudioQualityFormat.Low -> streamingData?.lowestQualityFormat
        AudioQualityFormat.Auto ->
            if (connectionMetered && isConnectionMeteredEnabled())
                streamingData?.mediumQualityFormat
            else
                streamingData?.autoMaxQualityFormat
    }

@UnstableApi
private fun getFormatUrl(
    videoId: String,
    cpn: String,
    responseJson: JsonObject,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
): Uri {
    val jsonString = Gson().toJson( responseJson )
    val playerResponse = jsonParser.decodeFromString<PlayerResponse>( jsonString )

    checkPlayability( playerResponse.playabilityStatus )

    val format = extractFormat( playerResponse.streamingData, audioQualityFormat, connectionMetered )
    Database.asyncTransaction {
        // Skip this step if it was added previously
        if( ::justAdded.isInitialized && justAdded == videoId ) return@asyncTransaction

        runBlocking( Dispatchers.IO ) {
            Innertube.nextPage( NextBody(videoId = videoId) )
                     ?.getOrNull()
                     ?.also {
                         it.itemsPage
                             ?.items
                             ?.firstOrNull()
                             ?.also( ::upsert )
                             ?.also {
                                 // Only add format when song is added to the database
                                 formatTable.insertIgnore(Format(
                                     videoId,
                                     format?.itag,
                                     format?.mimeType,
                                     format?.bitrate?.toLong(),
                                     format?.contentLength,
                                     format?.lastModified,
                                     format?.loudnessDb?.toFloat()
                                 ))
                             }
                     }
        }

        justAdded = videoId
    }

    return YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated( videoId, format?.url.orEmpty() )
                                         .toUri()
                                         .buildUpon()
                                         .appendQueryParameter( "range", "0-${format?.contentLength ?: 1_000_000}" )
                                         .appendQueryParameter( "cpn", cpn )
                                         .build()
}

@UnstableApi
fun getAndroidReelFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val cpn = CharUtils.randomString( 16 )
    val response = YoutubeStreamHelper.getAndroidReelPlayerResponse( ContentCountry.DEFAULT, Localization.DEFAULT, videoId, cpn )
    return getFormatUrl( videoId, cpn, response, audioQualityFormat, connectionMetered )
}

private fun String.getPoToken(): String? =
    this.replace("[", "")
        .replace("]", "")
        .split(",")
        .findLast { it.contains("\"") }
        ?.replace("\"", "")

private suspend fun generateIosPoToken() =
    createPoTokenChallenge().bodyAsText()
        .let { challenge ->
            val listChallenge = jsonParser.decodeFromString<List<String?>>(challenge)
            listChallenge.filterIsInstance<String>().firstOrNull()
        }?.let { poTokenChallenge ->
            Innertube.generatePoToken(poTokenChallenge)
                .bodyAsText()
                .getPoToken()
        }

@UnstableApi
suspend fun getIosFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val cpn = CharUtils.randomString( 16 )
    val visitorData = Store.getIosVisitorData()
    val playerRequestToken = generateIosPoToken().orEmpty()
    val poTokenResult = PoTokenResult(visitorData, playerRequestToken, null )
    val response = YoutubeStreamHelper.getIosPlayerResponse( ContentCountry.DEFAULT, Localization.DEFAULT, videoId, cpn, poTokenResult )
    return getFormatUrl( videoId, cpn, response, audioQualityFormat, connectionMetered )
}
//</editor-fold>

@UnstableApi
fun DataSpec.process(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): DataSpec = runBlocking( Dispatchers.IO ) {
    val formatUri = runBlocking( Dispatchers.IO ) {
        try {
            getAndroidReelFormatUrl( videoId, audioQualityFormat, connectionMetered )
        } catch ( e: Exception ) {
            when( e ) {
                is LoginRequiredException,
                is UnplayableException -> getIosFormatUrl( videoId, audioQualityFormat, connectionMetered )
                else -> throw e
            }
        }
    }

    withUri( formatUri ).subrange( uriPositionOffset )
}

//<editor-fold defaultstate="collapsed" desc="Data source factories">
@UnstableApi
fun PlayerServiceModern.createDataSourceFactory(): DataSource.Factory =
    ResolvingDataSource.Factory(
        CacheDataSource.Factory()
                       .setCache( downloadCache )
                       .setUpstreamDataSourceFactory(
                           CacheDataSource.Factory()
                                          .setCache( cache )
                                          .setUpstreamDataSourceFactory(
                                              appContext().okHttpDataSourceFactory
                                          )
                       )
                       .setCacheWriteDataSinkFactory( null )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
    ) { dataSpec ->
        Database.asyncTransaction {
            runBlocking( Dispatchers.Main ) {
                player.currentMediaItem
            }?.also( ::insertIgnore )
        }

        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")

        val isLocal = dataSpec.uri.scheme == ContentResolver.SCHEME_CONTENT || dataSpec.uri.scheme == ContentResolver.SCHEME_FILE
        val isCached = cache.isCached( videoId, dataSpec.position, CHUNK_LENGTH )
        val isDownloaded = downloadCache.isCached( videoId, dataSpec.position, CHUNK_LENGTH )

        return@Factory if( isLocal || isCached || isDownloaded )
            // No need to fetch online for already cached data
            dataSpec
        else
            dataSpec.process( videoId, audioQualityFormat, applicationContext.isConnectionMetered() )
    }

@UnstableApi
fun MyDownloadHelper.createDataSourceFactory(): DataSource.Factory =
    ResolvingDataSource.Factory(
        CacheDataSource.Factory()
                       .setCache( getDownloadCache(appContext()) )
                       .apply {
                           setUpstreamDataSourceFactory(
                               appContext().okHttpDataSourceFactory
                           )
                           setCacheWriteDataSinkFactory( null )
                       }
    ) { dataSpec: DataSpec ->
        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")

        val isDownloaded = downloadCache.isCached( videoId, dataSpec.position, CHUNK_LENGTH )

        return@Factory if( isDownloaded )
            // No need to fetch online for already cached data
            dataSpec
        else
            dataSpec.process( videoId, audioQualityFormat, appContext().isConnectionMetered() )
    }
//</editor-fold>
