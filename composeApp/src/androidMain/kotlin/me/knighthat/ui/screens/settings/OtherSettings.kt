package me.knighthat.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import io.ktor.http.Url
import it.fast4x.compose.persist.persistList
import it.fast4x.innertube.utils.parseCookieString
import it.fast4x.piped.Piped
import it.fast4x.piped.models.Instance
import it.fast4x.rimusic.R
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.PopupType
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.enums.ValidationType
import it.fast4x.rimusic.extensions.discord.DiscordLoginAndGetToken
import it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin
import it.fast4x.rimusic.service.PlayerMediaBrowserService
import it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.DefaultDialog
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.Menu
import it.fast4x.rimusic.ui.components.themed.MenuEntry
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import it.fast4x.rimusic.ui.components.themed.SmartMessage
import it.fast4x.rimusic.ui.screens.settings.ButtonBarSettingEntry
import it.fast4x.rimusic.ui.screens.settings.EnumValueSelectorSettingsEntry
import it.fast4x.rimusic.ui.screens.settings.ImportantSettingsDescription
import it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import it.fast4x.rimusic.ui.screens.settings.SettingsEntry
import it.fast4x.rimusic.ui.screens.settings.SettingsEntryGroupText
import it.fast4x.rimusic.ui.screens.settings.SettingsGroupSpacer
import it.fast4x.rimusic.ui.screens.settings.StringListValueSelectorSettingsEntry
import it.fast4x.rimusic.ui.screens.settings.SwitchSettingEntry
import it.fast4x.rimusic.ui.screens.settings.TextDialogSettingEntry
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.CheckAvailableNewVersion
import it.fast4x.rimusic.utils.TextCopyToClipboard
import it.fast4x.rimusic.utils.checkUpdateStateKey
import it.fast4x.rimusic.utils.defaultFolderKey
import it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import it.fast4x.rimusic.utils.enableYouTubeLoginKey
import it.fast4x.rimusic.utils.extraspaceKey
import it.fast4x.rimusic.utils.isAtLeastAndroid10
import it.fast4x.rimusic.utils.isAtLeastAndroid12
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.isAtLeastAndroid81
import it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import it.fast4x.rimusic.utils.isIgnoringBatteryOptimizations
import it.fast4x.rimusic.utils.isInvincibilityEnabledKey
import it.fast4x.rimusic.utils.isKeepScreenOnEnabledKey
import it.fast4x.rimusic.utils.isPipedCustomEnabledKey
import it.fast4x.rimusic.utils.isPipedEnabledKey
import it.fast4x.rimusic.utils.isProxyEnabledKey
import it.fast4x.rimusic.utils.logDebugEnabledKey
import it.fast4x.rimusic.utils.navigationBarPositionKey
import it.fast4x.rimusic.utils.parentalControlEnabledKey
import it.fast4x.rimusic.utils.pipedApiBaseUrlKey
import it.fast4x.rimusic.utils.pipedApiTokenKey
import it.fast4x.rimusic.utils.pipedInstanceNameKey
import it.fast4x.rimusic.utils.pipedPasswordKey
import it.fast4x.rimusic.utils.pipedUsernameKey
import it.fast4x.rimusic.utils.proxyHostnameKey
import it.fast4x.rimusic.utils.proxyModeKey
import it.fast4x.rimusic.utils.proxyPortKey
import it.fast4x.rimusic.utils.rememberEncryptedPreference
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.restartActivityKey
import it.fast4x.rimusic.utils.showFoldersOnDeviceKey
import it.fast4x.rimusic.utils.thumbnailRoundnessKey
import it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import it.fast4x.rimusic.utils.ytAccountEmailKey
import it.fast4x.rimusic.utils.ytAccountNameKey
import it.fast4x.rimusic.utils.ytCookieKey
import it.fast4x.rimusic.utils.ytVisitorDataKey
import kotlinx.coroutines.launch
import me.knighthat.colorPalette
import me.knighthat.thumbnailShape
import timber.log.Timber
import java.io.File
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("BatteryLife")
@ExperimentalAnimationApi
@Composable
fun OtherSettings() {
    val context = LocalContext.current
    val (colorPalette, typography, thumbnailShape) = LocalAppearance.current
    val thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    var isAndroidAutoEnabled by remember {
        val component = ComponentName(context, PlayerMediaBrowserService::class.java)
        val disabledFlag = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val enabledFlag = PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        mutableStateOf(
            value = context.packageManager.getComponentEnabledSetting(component) == enabledFlag,
            policy = object : SnapshotMutationPolicy<Boolean> {
                override fun equivalent(a: Boolean, b: Boolean): Boolean {
                    context.packageManager.setComponentEnabledSetting(
                        component,
                        if (b) enabledFlag else disabledFlag,
                        PackageManager.DONT_KILL_APP
                    )
                    return a == b
                }
            }
        )
    }

    var isInvincibilityEnabled by rememberPreference(isInvincibilityEnabledKey, false)

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations)
    }

    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations
        }

    var isProxyEnabled by rememberPreference(isProxyEnabledKey, false)
    var proxyHost by rememberPreference(proxyHostnameKey, "")
    var proxyPort by rememberPreference(proxyPortKey, 1080)
    var proxyMode by rememberPreference(proxyModeKey, Proxy.Type.HTTP)

    var defaultFolder by rememberPreference(defaultFolderKey, "/")

    var isKeepScreenOnEnabled by rememberPreference(isKeepScreenOnEnabledKey, false)

    var checkUpdateState by rememberPreference(checkUpdateStateKey, CheckUpdateState.Disabled)

    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    var showFolders by rememberPreference(showFoldersOnDeviceKey, true)

    var blackListedPaths by remember {
        val file = File(context.filesDir, "Blacklisted_paths.txt")
        if (file.exists()) {
            mutableStateOf(file.readLines())
        } else {
            mutableStateOf(emptyList())
        }
    }

    var parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)

    var restartActivity by rememberPreference(restartActivityKey, false)

    var extraspace by rememberPreference(extraspaceKey, false)

    Column(
        modifier = Modifier
            .background(colorPalette.background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
            .verticalScroll(rememberScrollState())
        /*
        .padding(
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues()
        )

         */
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.tab_miscellaneous),
            iconId = R.drawable.equalizer,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        SettingsEntryGroupText(title = stringResource(R.string.check_update))

        var checkUpdateNow by remember { mutableStateOf(false) }
        if (checkUpdateNow)
            CheckAvailableNewVersion(
                onDismiss = { checkUpdateNow = false },
                updateAvailable = {
                    if (!it)
                        SmartMessage(
                            context.resources.getString(R.string.info_no_update_available),
                            type = PopupType.Info,
                            context = context
                        )
                }
            )

        EnumValueSelectorSettingsEntry(
            title = stringResource(R.string.enable_check_for_update),
            selectedValue = checkUpdateState,
            onValueSelected = { checkUpdateState = it },
            valueText = {
                when (it) {
                    CheckUpdateState.Disabled -> stringResource(R.string.vt_disabled)
                    CheckUpdateState.Enabled -> stringResource(R.string.enabled)
                    CheckUpdateState.Ask -> stringResource(R.string.ask)
                }

            }
        )
        SettingsDescription(text = stringResource(R.string.when_enabled_a_new_version_is_checked_and_notified_during_startup))
        AnimatedVisibility(visible = checkUpdateState != CheckUpdateState.Disabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsDescription(
                    text = stringResource(R.string.check_update),
                    important = true,
                    modifier = Modifier.weight(1f)
                )

                SecondaryTextButton(
                    text = stringResource(R.string.info_check_update_now),
                    onClick = { checkUpdateNow = true },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 24.dp)
                )
            }
        }


        // rememberEncryptedPreference only works correct with API 24 and up

        /****** YOUTUBE LOGIN ******/

        var isYouTubeLoginEnabled by rememberPreference(enableYouTubeLoginKey, false)
        var loginYouTube by remember { mutableStateOf(false) }
        var visitorData by rememberEncryptedPreference(key = ytVisitorDataKey, defaultValue = "")
        var cookie by rememberEncryptedPreference(key = ytCookieKey, defaultValue = "")
        var accountName by rememberEncryptedPreference(key = ytAccountNameKey, defaultValue = "")
        var accountEmail by rememberEncryptedPreference(key = ytAccountEmailKey, defaultValue = "")
        var accountChannelHandle by rememberEncryptedPreference(
            key = ytAccountChannelHandleKey,
            defaultValue = ""
        )
        val isLoggedIn = remember(cookie) {
            "SAPISID" in parseCookieString(cookie)
        }

        SettingsGroupSpacer()
        SettingsEntryGroupText(title = "YOUTUBE MUSIC")

        SwitchSettingEntry(
            title = "Enable YouTube Music Login",
            text = "",
            isChecked = isYouTubeLoginEnabled,
            onCheckedChange = { isYouTubeLoginEnabled = it }
        )

        AnimatedVisibility(visible = isYouTubeLoginEnabled) {
            Column(
                modifier = Modifier.padding(start = 25.dp)
            ) {
                if (isAtLeastAndroid7) {
                    Column {
                        ButtonBarSettingEntry(
                            isEnabled = true,
                            title = if (isLoggedIn) "Disconnect" else "Connect",
                            text = if (isLoggedIn) "$accountName ${accountChannelHandle}" else "",
                            icon = R.drawable.logo_youtube,
                            iconColor = colorPalette().text,
                            onClick = {
                                if (isLoggedIn) {
                                    cookie = ""
                                    accountName = ""
                                    accountChannelHandle = ""
                                    accountEmail = ""
                                    visitorData = ""
                                    loginYouTube = false
                                } else
                                    loginYouTube = true
                            }
                        )
                        /*
                        ImportantSettingsDescription(
                            text = "You need to log in to listen the songs online"
                        )
                         */
                        SettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))

                        CustomModalBottomSheet(
                            showSheet = loginYouTube,
                            onDismissRequest = {
                                SmartMessage(
                                    "Restart RiMusic, please",
                                    type = PopupType.Info,
                                    context = context
                                )
                                loginYouTube = false
                                restartActivity = !restartActivity
                            },
                            containerColor = colorPalette().background0,
                            contentColor = colorPalette().background0,
                            modifier = Modifier.fillMaxWidth(),
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            dragHandle = {
                                Surface(
                                    modifier = Modifier.padding(vertical = 0.dp),
                                    color = colorPalette().background0,
                                    shape = thumbnailShape()
                                ) {}
                            },
                            shape = thumbnailRoundness.shape()
                        ) {
                            YouTubeLogin(
                                onLogin = { success ->
                                    if (success) {
                                        loginYouTube = false
                                        SmartMessage(
                                            "Login successful, restart RiMusic",
                                            type = PopupType.Info,
                                            context = context
                                        )
                                        restartActivity = !restartActivity
                                    }
                                }
                            )
                        }
                    }

                }
            }
        }

        /****** YOUTUBE LOGIN ******/

        /****** PIPED ******/

        // rememberEncryptedPreference only works correct with API 24 and up
        if (isAtLeastAndroid7) {
            var isPipedEnabled by rememberPreference(isPipedEnabledKey, false)
            var isPipedCustomEnabled by rememberPreference(isPipedCustomEnabledKey, false)
            var pipedUsername by rememberEncryptedPreference(pipedUsernameKey, "")
            var pipedPassword by rememberEncryptedPreference(pipedPasswordKey, "")
            var pipedInstanceName by rememberEncryptedPreference(pipedInstanceNameKey, "")
            var pipedApiBaseUrl by rememberEncryptedPreference(pipedApiBaseUrlKey, "")
            var pipedApiToken by rememberEncryptedPreference(pipedApiTokenKey, "")

            var loadInstances by rememberSaveable { mutableStateOf(false) }
            var isLoading by rememberSaveable { mutableStateOf(false) }
            var instances by persistList<Instance>(tag = "otherSettings/pipedInstances")
            var noInstances by rememberSaveable { mutableStateOf(false) }
            var executeLogin by rememberSaveable { mutableStateOf(false) }
            var showInstances by rememberSaveable { mutableStateOf(false) }
            var session by rememberSaveable {
                mutableStateOf<Result<it.fast4x.piped.models.Session>?>(
                    null
                )
            }

            val menuState = LocalMenuState.current
            val coroutineScope = rememberCoroutineScope()

            if (isLoading)
                DefaultDialog(
                    onDismiss = {
                        isLoading = false
                    }
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

            if (loadInstances) {
                LaunchedEffect(Unit) {
                    isLoading = true
                    Piped.getInstances()?.getOrNull()?.let {
                        instances = it
                        //println("mediaItem Instances $it")
                    } ?: run { noInstances = true }
                    isLoading = false
                    showInstances = true
                }
            }
            if (noInstances) {
                SmartMessage("No instances found", type = PopupType.Info, context = context)
            }

            if (executeLogin) {
                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        isLoading = true
                        session = Piped.login(
                            apiBaseUrl = Url(pipedApiBaseUrl), //instances[instanceSelected!!].apiBaseUrl,
                            username = pipedUsername,
                            password = pipedPassword
                        )?.onFailure {
                            Timber.e("Failed piped login ${it.stackTraceToString()}")
                            isLoading = false
                            SmartMessage(
                                "Piped login failed",
                                type = PopupType.Error,
                                context = context
                            )
                            loadInstances = false
                            session = null
                            executeLogin = false
                        }
                        if (session?.isSuccess == false)
                            return@launch

                        SmartMessage(
                            "Piped login successful",
                            type = PopupType.Success,
                            context = context
                        )
                        Timber.i("Piped login successful")

                        session.let {
                            it?.getOrNull()?.token?.let { it1 ->
                                pipedApiToken = it1
                                pipedApiBaseUrl = it.getOrNull()!!.apiBaseUrl.toString()
                            }
                        }

                        isLoading = false
                        loadInstances = false
                        executeLogin = false
                    }
                }
            }

            if (showInstances && instances.isNotEmpty()) {
                menuState.display {
                    Menu {
                        MenuEntry(
                            icon = R.drawable.chevron_back,
                            text = stringResource(R.string.cancel),
                            onClick = {
                                showInstances = false
                                menuState.hide()
                            }
                        )
                        instances.forEach {
                            MenuEntry(
                                icon = R.drawable.server,
                                text = it.name,
                                secondaryText = "${it.locationsFormatted} Users: ${it.userCount}",
                                onClick = {
                                    menuState.hide()
                                    pipedApiBaseUrl = it.apiBaseUrl.toString()
                                    pipedInstanceName = it.name
                                    /*
                                    instances.indexOf(it).let { index ->
                                        //instances[index].apiBaseUrl
                                        instanceSelected = index
                                        //println("mediaItem Instance ${instances[index].apiBaseUrl}")
                                    }
                                     */
                                    showInstances = false
                                }
                            )
                        }
                        MenuEntry(
                            icon = R.drawable.chevron_back,
                            text = stringResource(R.string.cancel),
                            onClick = {
                                showInstances = false
                                menuState.hide()
                            }
                        )
                    }
                }
            }




            SettingsGroupSpacer()
            SettingsEntryGroupText(title = stringResource(R.string.piped_account))
            SwitchSettingEntry(
                title = stringResource(R.string.enable_piped_syncronization),
                text = "",
                isChecked = isPipedEnabled,
                onCheckedChange = { isPipedEnabled = it }
            )

            AnimatedVisibility(visible = isPipedEnabled) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SwitchSettingEntry(
                        title = stringResource(R.string.piped_custom_instance),
                        text = "",
                        isChecked = isPipedCustomEnabled,
                        onCheckedChange = { isPipedCustomEnabled = it }
                    )
                    AnimatedVisibility(visible = isPipedCustomEnabled) {
                        Column {
                            TextDialogSettingEntry(
                                title = stringResource(R.string.piped_custom_instance),
                                text = pipedApiBaseUrl,
                                currentText = pipedApiBaseUrl,
                                onTextSave = {
                                    pipedApiBaseUrl = it
                                }
                            )
                        }
                    }
                    AnimatedVisibility(visible = !isPipedCustomEnabled) {
                        Column {
                            ButtonBarSettingEntry(
                                //isEnabled = pipedApiToken.isEmpty(),
                                title = stringResource(R.string.piped_change_instance),
                                text = pipedInstanceName,
                                icon = R.drawable.open,
                                onClick = {
                                    loadInstances = true
                                }
                            )
                        }
                    }

                    TextDialogSettingEntry(
                        //isEnabled = pipedApiToken.isEmpty(),
                        title = stringResource(R.string.piped_username),
                        text = pipedUsername,
                        currentText = pipedUsername,
                        onTextSave = { pipedUsername = it }
                    )
                    TextDialogSettingEntry(
                        //isEnabled = pipedApiToken.isEmpty(),
                        title = stringResource(R.string.piped_password),
                        text = if (pipedPassword.isNotEmpty()) "********" else "",
                        currentText = pipedPassword,
                        onTextSave = { pipedPassword = it },
                        modifier = Modifier
                            .semantics {
                                password()
                            }
                    )

                    ButtonBarSettingEntry(
                        isEnabled = pipedPassword.isNotEmpty() && pipedUsername.isNotEmpty() && pipedApiBaseUrl.isNotEmpty(),
                        title = if (pipedApiToken.isNotEmpty()) stringResource(R.string.piped_disconnect) else stringResource(
                            R.string.piped_connect
                        ),
                        text = if (pipedApiToken.isNotEmpty()) stringResource(R.string.piped_connected_to_s).format(
                            pipedInstanceName
                        ) else "",
                        icon = R.drawable.piped_logo,
                        iconColor = colorPalette.red,
                        onClick = {
                            if (pipedApiToken.isNotEmpty()) {
                                pipedApiToken = ""
                                executeLogin = false
                            } else executeLogin = true
                        }
                    )

                }
            }
        }

        /****** PIPED ******/

        /****** DISCORD ******/

        // rememberEncryptedPreference only works correct with API 24 and up
        if (isAtLeastAndroid7) {
            var isDiscordPresenceEnabled by rememberPreference(isDiscordPresenceEnabledKey, false)
            var loginDiscord by remember { mutableStateOf(false) }
            var discordPersonalAccessToken by rememberEncryptedPreference(
                key = discordPersonalAccessTokenKey,
                defaultValue = ""
            )
            SettingsGroupSpacer()
            SettingsEntryGroupText(title = stringResource(R.string.social_discord))
            SwitchSettingEntry(
                isEnabled = isAtLeastAndroid81,
                title = stringResource(R.string.discord_enable_rich_presence),
                text = "",
                isChecked = isDiscordPresenceEnabled,
                onCheckedChange = { isDiscordPresenceEnabled = it }
            )

            AnimatedVisibility(visible = isDiscordPresenceEnabled) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    ButtonBarSettingEntry(
                        isEnabled = true,
                        title = if (discordPersonalAccessToken.isNotEmpty()) stringResource(R.string.discord_disconnect) else stringResource(
                            R.string.discord_connect
                        ),
                        text = if (discordPersonalAccessToken.isNotEmpty()) stringResource(R.string.discord_connected_to_discord_account) else "",
                        icon = R.drawable.logo_discord,
                        iconColor = colorPalette.text,
                        onClick = {
                            if (discordPersonalAccessToken.isNotEmpty())
                                discordPersonalAccessToken = ""
                            else
                                loginDiscord = true
                        }
                    )

                    CustomModalBottomSheet(
                        showSheet = loginDiscord,
                        onDismissRequest = {
                            loginDiscord = false
                        },
                        containerColor = colorPalette.background0,
                        contentColor = colorPalette.background0,
                        modifier = Modifier.fillMaxWidth(),
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        dragHandle = {
                            Surface(
                                modifier = Modifier.padding(vertical = 0.dp),
                                color = colorPalette.background0,
                                shape = thumbnailShape
                            ) {}
                        },
                        shape = thumbnailRoundness.shape()
                    ) {
                        DiscordLoginAndGetToken(
                            rememberNavController(),
                            onGetToken = { token ->
                                loginDiscord = false
                                discordPersonalAccessToken = token
                                SmartMessage(token, type = PopupType.Info, context = context)
                            }
                        )
                    }
                }
            }
        }

        /****** DISCORD ******/

        SettingsGroupSpacer()
        SettingsEntryGroupText(stringResource(R.string.on_device))
        StringListValueSelectorSettingsEntry(
            title = stringResource(R.string.blacklisted_folders),
            text = stringResource(R.string.edit_blacklist_for_on_device_songs),
            addTitle = stringResource(R.string.add_folder),
            addPlaceholder = if (isAtLeastAndroid10) {
                "Android/media/com.whatsapp/WhatsApp/Media"
            } else {
                "/storage/emulated/0/Android/media/com.whatsapp/"
            },
            conflictTitle = stringResource(R.string.this_folder_already_exists),
            removeTitle = stringResource(R.string.are_you_sure_you_want_to_remove_this_folder_from_the_blacklist),
            context = LocalContext.current,
            list = blackListedPaths,
            add = { newPath ->
                blackListedPaths = blackListedPaths + newPath
                val file = File(context.filesDir, "Blacklisted_paths.txt")
                file.writeText(blackListedPaths.joinToString("\n"))
            },
            remove = { path ->
                blackListedPaths = blackListedPaths.filter { it != path }
                val file = File(context.filesDir, "Blacklisted_paths.txt")
                file.writeText(blackListedPaths.joinToString("\n"))
            }
        )

        SwitchSettingEntry(
            title = stringResource(R.string.folders),
            text = stringResource(R.string.show_folders_in_on_device_page),
            isChecked = showFolders,
            onCheckedChange = { showFolders = it }
        )
        AnimatedVisibility(visible = showFolders) {
            TextDialogSettingEntry(
                title = stringResource(R.string.folder_that_will_show_when_you_open_on_device_page),
                text = defaultFolder,
                currentText = defaultFolder,
                onTextSave = { defaultFolder = it }
            )
        }

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = stringResource(R.string.android_auto))

        SettingsDescription(text = stringResource(R.string.enable_unknown_sources))

        SwitchSettingEntry(
            title = stringResource(R.string.android_auto_1),
            text = stringResource(R.string.enable_android_auto_support),
            isChecked = isAndroidAutoEnabled,
            onCheckedChange = { isAndroidAutoEnabled = it }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = stringResource(R.string.androidheadunit))
        SwitchSettingEntry(
            title = stringResource(R.string.extra_space),
            text = "",
            isChecked = extraspace,
            onCheckedChange = { extraspace = it }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = stringResource(R.string.service_lifetime))

        SwitchSettingEntry(
            title = stringResource(R.string.keep_screen_on),
            text = stringResource(R.string.prevents_screen_timeout),
            isChecked = isKeepScreenOnEnabled,
            onCheckedChange = { isKeepScreenOnEnabled = it }
        )

        ImportantSettingsDescription(text = stringResource(R.string.battery_optimizations_applied))

        if (isAtLeastAndroid12) {
            SettingsDescription(text = stringResource(R.string.is_android12))
        }

        val msgNoBatteryOptim = stringResource(R.string.not_find_battery_optimization_settings)

        SettingsEntry(
            title = stringResource(R.string.ignore_battery_optimizations),
            isEnabled = !isIgnoringBatteryOptimizations,
            text = if (isIgnoringBatteryOptimizations) {
                stringResource(R.string.already_unrestricted)
            } else {
                stringResource(R.string.disable_background_restrictions)
            },
            onClick = {
                if (!isAtLeastAndroid6) return@SettingsEntry

                try {
                    activityResultLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (e: ActivityNotFoundException) {
                    try {
                        activityResultLauncher.launch(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    } catch (e: ActivityNotFoundException) {
                        SmartMessage(
                            "$msgNoBatteryOptim RiMusic",
                            type = PopupType.Info,
                            context = context
                        )
                    }
                }
            }
        )

        SwitchSettingEntry(
            title = stringResource(R.string.invincible_service),
            text = stringResource(R.string.turning_off_battery_optimizations_is_not_enough),
            isChecked = isInvincibilityEnabled,
            onCheckedChange = { isInvincibilityEnabled = it }
        )

        SettingsGroupSpacer()

        SettingsGroupSpacer()
        SettingsEntryGroupText(title = stringResource(R.string.proxy))
        SettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))
        SwitchSettingEntry(
            title = stringResource(R.string.enable_proxy),
            text = "",
            isChecked = isProxyEnabled,
            onCheckedChange = { isProxyEnabled = it }
        )

        AnimatedVisibility(visible = isProxyEnabled) {
            Column {
                EnumValueSelectorSettingsEntry(title = stringResource(R.string.proxy_mode),
                    selectedValue = proxyMode,
                    onValueSelected = { proxyMode = it },
                    valueText = { it.name }
                )
                TextDialogSettingEntry(
                    title = stringResource(R.string.proxy_host),
                    text = proxyHost, //stringResource(R.string.set_proxy_hostname),
                    currentText = proxyHost,
                    onTextSave = { proxyHost = it },
                    validationType = ValidationType.Ip
                )
                TextDialogSettingEntry(
                    title = stringResource(R.string.proxy_port),
                    text = proxyPort.toString(), //stringResource(R.string.set_proxy_port),
                    currentText = proxyPort.toString(),
                    onTextSave = { proxyPort = it.toIntOrNull() ?: 1080 })
            }
        }

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = stringResource(R.string.parental_control))

        SwitchSettingEntry(
            title = stringResource(R.string.parental_control),
            text = stringResource(R.string.info_prevent_play_songs_with_age_limitation),
            isChecked = parentalControlEnabled,
            onCheckedChange = { parentalControlEnabled = it }
        )


        SettingsGroupSpacer()

        var text by remember { mutableStateOf(null as String?) }
        var copyToClipboard by remember {
            mutableStateOf(false)
        }

        if (copyToClipboard) text?.let {
            TextCopyToClipboard(it)
            copyToClipboard = false
        }

        val noLogAvailable = stringResource(R.string.no_log_available)

        SettingsEntryGroupText(title = stringResource(R.string.debug))
        SwitchSettingEntry(
            title = stringResource(R.string.enable_log_debug),
            text = stringResource(R.string.if_enabled_create_a_log_file_to_highlight_errors),
            isChecked = logDebugEnabled,
            onCheckedChange = {
                logDebugEnabled = it
                if (!it) {
                    val file = File(context.filesDir.resolve("logs"), "RiMusic_log.txt")
                    if (file.exists())
                        file.delete()

                    val filec = File(context.filesDir.resolve("logs"), "RiMusic_crash_log.txt")
                    if (filec.exists())
                        filec.delete()


                } else
                    SmartMessage(
                        context.resources.getString(R.string.restarting_rimusic_is_required),
                        type = PopupType.Info, context = context
                    )
            }
        )
        ImportantSettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))
        ButtonBarSettingEntry(
            isEnabled = logDebugEnabled,
            title = stringResource(R.string.copy_log_to_clipboard),
            text = "",
            icon = R.drawable.copy,
            onClick = {
                val file = File(context.filesDir.resolve("logs"), "RiMusic_log.txt")
                if (file.exists()) {
                    text = file.readText()
                    copyToClipboard = true
                } else
                    SmartMessage(noLogAvailable, type = PopupType.Info, context = context)
            }
        )
        ButtonBarSettingEntry(
            isEnabled = logDebugEnabled,
            title = stringResource(R.string.copy_crash_log_to_clipboard),
            text = "",
            icon = R.drawable.copy,
            onClick = {
                val file = File(context.filesDir.resolve("logs"), "RiMusic_crash_log.txt")
                if (file.exists()) {
                    text = file.readText()
                    copyToClipboard = true
                } else
                    SmartMessage(noLogAvailable, type = PopupType.Info, context = context)
            }
        )

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}
