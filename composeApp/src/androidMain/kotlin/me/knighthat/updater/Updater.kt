package me.knighthat.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import app.kreate.android.BuildConfig
import app.kreate.android.R
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import it.fast4x.rimusic.ui.screens.settings.EnumValueSelectorSettingsEntry
import it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import it.fast4x.rimusic.utils.checkUpdateStateKey
import it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.knighthat.utils.Repository
import me.knighthat.utils.Toaster
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.net.UnknownHostException
import java.time.ZonedDateTime

object Updater {

    private val JSON = Json {
        ignoreUnknownKeys = true
    }
    private val isUpdatable: Boolean =
        !BuildConfig.DEBUG && !BuildConfig.VERSION_NAME.endsWith("fdroid", true )

    lateinit var build: GithubRelease.Build

    private fun extractBuild( assets: List<GithubRelease.Build> ): GithubRelease.Build {
        val appName = BuildConfig.APP_NAME
        val buildType = BuildConfig.BUILD_TYPE

        /*
           IDE will complain that is condition is always true
           but because it only sees debug, it assumes the results
           of this evaluation. DO NOT remove this!
         */
        if( buildType != "full" && buildType != "minified" )
            throw IllegalStateException( "Unknown build type ${BuildConfig.BUILD_TYPE}" )

        // Get the first build that has name matches 'Kreate-<buildType>.apk'
        // e.g. Full version will have name 'Kreate-full.apk'
        val fileName = "$appName-$buildType.apk"
        return assets.fastFirstOrNull {    // Experimental, revert to firstOrNull if needed
            it.name == fileName
        } ?: throw IOException( "File $fileName is not available for download!" )
    }

    /**
     * Sends out requests to Github for latest version.
     *
     * Results are downloaded, filtered, and saved to [build]
     *
     * > **NOTE**: This is a blocking process, it should never run on UI thread
     */
    private suspend fun fetchUpdate() = withContext( Dispatchers.IO ) {
        val client = OkHttpClient()

        // https://api.github.com/repos/knighthat/Kreate/releases/latest
        val url = "${Repository.GITHUB_API}/repos/${Repository.LATEST_TAG_URL}"
        val request = Request.Builder().url( url ).build()
        val response = client.newCall( request ).execute()

        if( response.isSuccessful ) {
            val resBody = response.body?.string() ?: return@withContext

            val githubRelease = JSON.decodeFromString<GithubRelease>( resBody )
            build = extractBuild( githubRelease.builds )
        }
    }

    fun checkForUpdate(
        isForced: Boolean = false
    ) = CoroutineScope( Dispatchers.IO ).launch {
        NewUpdateAvailableDialog.isCancelled = !isUpdatable
        if( !isUpdatable ) return@launch

        try {
            if(!::build.isInitialized || isForced)
                fetchUpdate()

            /**
             * Project's build time will always be earlier APK creation time.
             * Therefore, the app will recognize it is behind the update even
             * when the user just updated.
             *
             * To counter this problem, time strings are converted into [ZonedDateTime]
             * and apk creation time is subtracted an hour before the comparison.
             */
            val projBuildTime = ZonedDateTime.parse( BuildConfig.BUILD_TIME )
            val upstreamBuildTime = build.buildTime.minusHours( 1L )

            NewUpdateAvailableDialog.isActive = upstreamBuildTime.isAfter( projBuildTime )
        } catch( e: Exception ) {
            var message = appContext().resources.getString( R.string.error_unknown )

            when( e ) {
                is UnknownHostException -> message = appContext().resources.getString( R.string.error_no_internet )
                else -> e.message?.let { message = it }
            }

            withContext( Dispatchers.Main ) { Toaster.e( message ) }

            NewUpdateAvailableDialog.isCancelled = true
        }
    }

    @Composable
    fun SettingEntry() {
        var checkUpdateState by rememberPreference( checkUpdateStateKey, CheckUpdateState.Disabled )
        if( !isUpdatable )
            checkUpdateState = CheckUpdateState.Disabled

        Row( Modifier.fillMaxWidth() ) {
            EnumValueSelectorSettingsEntry(
                title = stringResource( R.string.enable_check_for_update ),
                selectedValue = checkUpdateState,
                onValueSelected = { checkUpdateState = it },
                valueText = { it.text },
                isEnabled = isUpdatable,
                modifier = Modifier.weight( 1f )
            )

            AnimatedVisibility(
                visible = checkUpdateState != CheckUpdateState.Disabled && isUpdatable,
                // Slide in from right + fade in effect.
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(initialAlpha = 0f),
                // Slide out from left + fade out effect.
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(targetAlpha = 0f)
            ) {
                SecondaryTextButton(
                    text = stringResource( R.string.info_check_update_now ),
                    onClick = { checkForUpdate( true ) },
                    modifier = Modifier.padding( end = 24.dp )
                )
            }
        }

        SettingsDescription(
            stringResource(
                if( isUpdatable )
                    R.string.when_enabled_a_new_version_is_checked_and_notified_during_startup
                else
                    R.string.description_app_not_installed_by_apk
            )
        )
    }
}