package me.knighthat.updater

import android.content.Context
import android.os.Looper
import androidx.compose.ui.util.fastFirstOrNull
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.NetworkService
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.utils.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import me.knighthat.updater.Updater.build
import me.knighthat.utils.Repository
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.nio.file.NoSuchFileException
import kotlin.time.ExperimentalTime


object Updater {
    private lateinit var tagName: String

    lateinit var build: GithubRelease.Build

    /**
     * @throws NoSuchFileException when there's no build matches current build
     */
    @Throws(NoSuchFileException::class)
    private fun extractBuild( assets: List<GithubRelease.Build> ): GithubRelease.Build {
        val filename = getFileName()
        return assets.fastFirstOrNull {
            // Get the first build that has name matches 'Kreate-<buildType>.apk'
            // with the exception of nightly build, which is `Kreate-nightly.apk`
            it.name == filename
        } ?: throw NoSuchFileException(filename)
    }

    /**
     * Turns `v1.0.0` to `1.0.0`, `1.0.0-m` to `1.0.0`
     */
    private fun trimVersion( versionStr: String ) = versionStr.removePrefix( "v" ).substringBefore( "-" )

    /**
     * @throws ResponseException when occurs while getting response from GitHub
     * @throws SerializationException when fails to deserialize response to [GithubRelease]
     */
    @Throws(ResponseException::class, SerializationException::class)
    private suspend fun getLatestRelease(): GithubRelease {
        // https://api.github.com/repos/knighthat/Kreate/releases/latest
        val url = "${Repository.GITHUB_API}/repos/${Repository.LATEST_TAG_URL}"

        return NetworkService.client
                             .get( url )
                             .body<GithubRelease>()
    }

    /**
     * @throws ResponseException when occurs while getting response from GitHub
     * @throws NoSuchElementException when no prerelease [GithubRelease] found
     * @throws SerializationException when fails to deserialize response to list of [GithubRelease]
     */
    @OptIn(ExperimentalTime::class)
    @Throws(ResponseException::class, NoSuchElementException::class, SerializationException::class)
    private suspend fun getPrerelease(): GithubRelease {
        // https://api.github.com/repos/knighthat/Kreate/releases
        val url = "${Repository.GITHUB_API}/repos/${Repository.REPO}/releases"

        return NetworkService.client
                             .get( url )
                             .body<List<GithubRelease>>()
                             .filter( GithubRelease::prerelease )
                             .sortedBy(GithubRelease::publishedAt )
                             .reversed()
                             .first()
    }

    /**
     * Sends out requests to Github for latest version.
     *
     * Results are downloaded, filtered, and saved to [build]
     *
     * > **NOTE**: This is a blocking process, it should never run on UI thread
     */
    private suspend fun fetchUpdate() {
        assert( Looper.myLooper() != Looper.getMainLooper() ) {
            "Cannot run fetch update on main thread"
        }

        // Ignore the warning `BuildConfig.FLAVOR_env == "nightly"` either `true` or `false`
        // This condition is different based on the build
        val release = if( BuildConfig.FLAVOR_env == "nightly" ) getPrerelease() else getLatestRelease()
        build = extractBuild( release.builds )
        tagName = release.tagName
    }

    fun checkForUpdate(
        context: Context,
        isForced: Boolean = false
    ) = CoroutineScope( Dispatchers.IO ).launch {
        if( ::build.isInitialized && !isForced )
            return@launch

        if( !isNetworkAvailable( context ) ) {
            Toaster.noInternet()
            return@launch
        }

        try {
            fetchUpdate()

            val isNewUpdateAvailable = trimVersion( BuildConfig.VERSION_NAME ) != trimVersion( tagName )
            if( !isNewUpdateAvailable && (Preferences.SHOW_CHECK_UPDATE_STATUS.value || isForced) )
                Toaster.i( R.string.info_no_update_available )

            when( Preferences.CHECK_UPDATE.value ) {
                CheckUpdateState.ASK                -> NewUpdatePrompt.isActive = isNewUpdateAvailable
                CheckUpdateState.DOWNLOAD_INSTALL   -> DownloadAndInstallDialog.isActive = isNewUpdateAvailable
                CheckUpdateState.DISABLED           -> NewUpdatePrompt.isActive = isForced && isNewUpdateAvailable
            }
        } catch( e: Exception ) {
            Timber.tag( "Updater" ).e( e )

            if( e is NoSuchElementException && Preferences.SHOW_CHECK_UPDATE_STATUS.value ) {
                Toaster.i( R.string.info_no_update_available )
                return@launch
            }

            val message = when( e ) {
                is NoSuchFileException -> context.getString( R.string.error_no_build_matches_this_version )

                is ResponseException,
                is SerializationException -> context.getString( R.string.error_check_for_updates_failed )

                else -> context.getString( R.string.error_unknown )
            }
            Toaster.e( message )
        }
    }

    fun getFileName(): String {
        // Ignore the warning `BuildConfig.FLAVOR_env == "nightly"` either `true` or `false`
        // This condition is different based on the build
        val suffix = if( BuildConfig.FLAVOR_env == "nightly" )
            BuildConfig.FLAVOR_env
        else when( BuildConfig.FLAVOR_arch ) {
            "universal" -> "release"
            "arm32"     -> "armeabi-v7a"
            "arm64"     -> "arm64-v8a"
            "x86"       -> "x86"
            "x86_64"    -> "x86_64"
            else -> throw IllegalStateException("Unknown architecture ${BuildConfig.FLAVOR_arch}")
        }
        // e.g. Release version will have name 'Kreate-release.apk'
        return "%s-%s.apk".format(BuildConfig.APP_NAME, suffix)
    }
}