package me.knighthat.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import app.kreate.android.BuildConfig
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.knighthat.component.dialog.Dialog
import me.knighthat.utils.Toaster
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.random.nextUInt


object DownloadAndInstallDialog: Dialog {

    private const val INITIALIZING = "initializing"
    private const val DOWNLOADING = "downloading"
    private const val VERIFYING = "verifying"
    private const val INSTALLING = "installing"
    private const val ERROR = "error"

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.word_updating )

    var stage: String by mutableStateOf( INITIALIZING )
    var errorMessage: String? by mutableStateOf( null )
    override var isActive: Boolean by mutableStateOf( false )

    private fun installApk( context: Context, apkFile: File ) {
        if ( !apkFile.exists() ) {
            errorMessage = context.getString( R.string.error_downloaded_file_not_found )
            stage = ERROR
            return
        }

        val apkUri = if(isAtLeastAndroid7 )
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider", // must match manifest
                apkFile
            )
        else
            apkFile.toUri()

        val installIntent = Intent( Intent.ACTION_VIEW ).apply {
            setDataAndType( apkUri, "application/vnd.android.package-archive" )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }

    private suspend fun downloadUpdate(
        context: Context,
        apkFile: File,
        onDataDownload: (Float) -> Unit
    ) {
        val downloadManager = context.getSystemService<DownloadManager>()
        if( downloadManager == null ) {
            errorMessage = context.getString( R.string.error_download_manager_init_failed )
            stage = ERROR
            return
        }

        // Create a download simulation instead of hitting CDN server multiple times
        // during development. But also allows download if file doesn't exist
        if( BuildConfig.DEBUG && apkFile.exists() ) {
            var bytesDownloaded = UInt.MIN_VALUE
            val tenPercent = Updater.build.size * 10u / 100u

            while( bytesDownloaded < Updater.build.size ) {
                val generatedBytes = Random(System.currentTimeMillis() ).nextUInt( tenPercent )
                bytesDownloaded += generatedBytes

                onDataDownload( generatedBytes.toFloat() / Updater.build.size.toFloat() )

                delay( 100 )
            }
        } else {
             val downloadId: Long = DownloadManager.Request( Updater.build.downloadUrl.toUri() )
                                                   .setDestinationUri( Uri.fromFile(apkFile) )
                                                   .let( downloadManager::enqueue )
            var isDownloading = true
            val query = DownloadManager.Query().setFilterById( downloadId )

            while ( isDownloading ) {
                val cursor = downloadManager.query( query )
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getInt(
                        cursor.getColumnIndexOrThrow( DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR )
                    )
                    val bytesTotal = cursor.getInt(
                        cursor.getColumnIndexOrThrow( DownloadManager.COLUMN_TOTAL_SIZE_BYTES )
                    )

                    if (bytesTotal > 0)
                        onDataDownload( bytesDownloaded.toFloat() / bytesTotal )

                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow( DownloadManager.COLUMN_STATUS )
                    )
                    isDownloading = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
                }
                cursor?.close()

                delay( 10 )
            }
        }

        stage = VERIFYING
    }

    private fun verifyDownload( context: Context, apkFile: File ) {
        if ( !apkFile.exists() ) {
            errorMessage = context.getString( R.string.error_downloaded_file_not_found )
            stage = ERROR
            return
        }

        // Calculate hash
        val buffer = ByteArray( 4096 )      // 4KiB per chunk
        val digest = MessageDigest.getInstance( "SHA-256" )

        FileInputStream( apkFile ).use { inStream ->
            var bytesRead: Int
            while( inStream.read( buffer ).also { bytesRead = it } != -1 )
                digest.update( buffer, 0, bytesRead )
        }

        // Constant-time comparison
        val buildHash: ByteArray = Updater.build
                                          .digest
                                          .orEmpty()
                                          .substringAfter( "sha256:" )
                                          .chunked( 2 )
                                          .map { it.toInt( 16 ).toByte() }
                                          .toByteArray()
        val calculatedHash = digest.digest()
        if( buildHash.size != calculatedHash.size ) {
            errorMessage = context.getString( R.string.error_failed_to_verify_download_file )
            stage = ERROR
            return
        }

        var result = 0
        for( i in buildHash.indices )
            result = result or (buildHash[i].toInt() xor calculatedHash[i].toInt())

        stage = if( result != 0 ) {
            errorMessage = context.getString( R.string.error_failed_to_verify_download_file )
            ERROR
        } else
            INSTALLING
    }

    override fun hideDialog() {
        if( stage == INITIALIZING || stage == DOWNLOADING )
            return

        super.hideDialog()
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current

        Box {
            var progress by remember { mutableFloatStateOf( 0f ) }
            val animatedProcess by animateFloatAsState(
                targetValue = progress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
            )
            CircularProgressIndicator(
                progress = { animatedProcess },
                modifier = Modifier.fillMaxWidth( .7f )
                                   .aspectRatio( 1f )
                                   .align( Alignment.Center ),
                strokeWidth = 15.dp,
                trackColor = colorPalette().textDisabled,
                color = if( stage == ERROR ) colorPalette().red else colorPalette().accent
            )

            val apkFile = remember {
                // Saved to user's Android/data/me.knighthat.kreate(.debug)/Kreate-<buildType>.apk
                File(
                    context.getExternalFilesDir( Environment.DIRECTORY_DOWNLOADS ),
                    Updater.build.name
                )
            }
            LaunchedEffect( stage ) {
                CoroutineScope(Dispatchers.IO ).launch {
                    when( stage ) {
                        INITIALIZING -> {
                            while( progress < .2f ) {
                                progress += Random( System.currentTimeMillis() ).nextDouble( .0, .02 ).toFloat()
                                delay( 100 )
                            }
                            stage = DOWNLOADING
                        }
                        DOWNLOADING -> {
                            delay( 1000L )

                            downloadUpdate( context, apkFile ) {
                                progress += it * 60 / 100
                            }
                        }
                        VERIFYING -> {
                            delay( 1000L )

                            verifyDownload( context, apkFile )
                            progress = 1f
                        }
                        INSTALLING -> {
                            delay( 200L )

                            installApk( context, apkFile )
                        }
                        ERROR -> {
                            errorMessage?.also( Toaster::e )

                            if( apkFile.exists() )
                                apkFile.deleteOnExit()
                        }
                    }
                }
            }
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
                },
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.align( Alignment.Center )
            ) { currentStage ->
                val textId by remember { derivedStateOf {
                    when( currentStage ) {
                        INITIALIZING    -> R.string.update_dialog_init_status
                        DOWNLOADING     -> R.string.update_dialog_downloading_status
                        VERIFYING       -> R.string.update_dialog_verifying_status
                        INSTALLING      -> R.string.update_dialog_installing_status
                        ERROR           -> R.string.update_dialog_error_status
                        else            -> R.string.error_unknown
                    }
                }}
                BasicText(
                    text = stringResource( textId ),
                    style = typography().m.copy( colorPalette().text ),
                )
            }
        }
    }
}