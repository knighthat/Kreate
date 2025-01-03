package me.knighthat.updater

import androidx.compose.animation.*
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
import it.fast4x.rimusic.BuildConfig
import it.fast4x.rimusic.R
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.enums.PopupType
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import it.fast4x.rimusic.ui.components.themed.SmartMessage
import it.fast4x.rimusic.ui.screens.settings.EnumValueSelectorSettingsEntry
import it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import it.fast4x.rimusic.utils.checkUpdateStateKey
import it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import me.knighthat.utils.Repository
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.net.UnknownHostException
import java.time.Instant

object Updater {

    private val JSON = Json {
        ignoreUnknownKeys = true
    }

    lateinit var build: GithubRelease.Build

    private fun extractBuild( assets: List<GithubRelease.Build> ): GithubRelease.Build {
        val flavor = BuildConfig.FLAVOR
        val buildType = BuildConfig.BUILD_TYPE

        if( buildType != "full" && buildType != "minified" )
            throw IllegalStateException( "Unknown build type ${BuildConfig.BUILD_TYPE}" )

        // Get the first build that has name matches 'RiMusic-<flavor>-<buildType>.apk'
        // e.g. Upstream full version will have name 'RiMusic-upstream-full.apk'
        val fileName = "RiMusic-$flavor-$buildType.apk"
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
    suspend fun fetchUpdate() = withContext( Dispatchers.IO ) {
        val client = OkHttpClient()

        val url = Repository.GITHUB_API.plus( Repository.API_TAG_PATH )
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
        try {
            if(!::build.isInitialized || isForced)
                fetchUpdate()

            val projBuildTime = Instant.parse(BuildConfig.BUILD_TIME)
            NewUpdateAvailableDialog.isActive = build.buildTime.isAfter(projBuildTime)
        } catch( e: Exception ) {
            var message = appContext().resources.getString( R.string.error_unknown )

            when( e ) {
                is UnknownHostException -> message = appContext().resources.getString( R.string.error_no_internet )
                else -> e.message?.let { message = it }
            }

            withContext( Dispatchers.Main ) {
                SmartMessage(
                    context = appContext(),
                    message = message,
                    type = PopupType.Error
                )
            }

            NewUpdateAvailableDialog.isCancelled = true
        }
    }

    @Composable
    fun SettingEntry() {
        var checkUpdateState by rememberPreference( checkUpdateStateKey, CheckUpdateState.Disabled )

        Row( Modifier.fillMaxWidth() ) {
            EnumValueSelectorSettingsEntry(
                title = stringResource( R.string.enable_check_for_update ),
                selectedValue = checkUpdateState,
                onValueSelected = { checkUpdateState = it },
                valueText = { it.text },
                modifier = Modifier.weight( 1f )
            )

            AnimatedVisibility(
                visible = checkUpdateState != CheckUpdateState.Disabled,
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

        SettingsDescription( stringResource( R.string.when_enabled_a_new_version_is_checked_and_notified_during_startup  )  )
    }
}