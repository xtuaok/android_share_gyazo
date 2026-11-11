package xtuaok.sharegyazo.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xtuaok.sharegyazo.BuildConfig
import xtuaok.sharegyazo.R
import xtuaok.sharegyazo.ShareGyazoApp
import xtuaok.sharegyazo.app
import xtuaok.sharegyazo.data.TapAction
import xtuaok.sharegyazo.ui.components.PreferenceCategory
import xtuaok.sharegyazo.ui.components.PreferenceItem
import xtuaok.sharegyazo.ui.components.SingleChoiceDialog
import xtuaok.sharegyazo.ui.components.SwitchPreference
import xtuaok.sharegyazo.upload.Notifications

private enum class SettingsDialog { DefaultProfile, TapAction }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenProfiles: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val profiles by app.profiles.profiles.collectAsStateWithLifecycle()
    val defaultUuid by app.profiles.defaultUuid.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.canPost(context)
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = it
        if (!it) {
            // Permanently denied: let the user enable it in system settings.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_activity_preference)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceCategory(stringResource(R.string.category_profiles))
            PreferenceItem(
                title = stringResource(R.string.server_profiles),
                summary = profiles.joinToString { it.name },
                onClick = onOpenProfiles,
            )
            PreferenceItem(
                title = stringResource(R.string.default_profile),
                summary = app.profiles.get(defaultUuid)?.name ?: stringResource(R.string.default_profile_none),
                onClick = { dialog = SettingsDialog.DefaultProfile },
            )

            PreferenceCategory(stringResource(R.string.category_behavior_url))
            SwitchPreference(
                title = stringResource(R.string.copy_gyazo_url_title),
                summary = stringResource(R.string.copy_gyazo_url_summary),
                checked = settings.copyUrl,
            ) { v -> app.settings.update { it.copy(copyUrl = v) } }
            SwitchPreference(
                title = stringResource(R.string.open_url),
                summary = stringResource(R.string.open_url_browser_summary),
                checked = settings.openBrowser,
            ) { v -> app.settings.update { it.withOpenBrowser(v) } }
            SwitchPreference(
                title = stringResource(R.string.share_url),
                summary = stringResource(R.string.share_url_text_summary),
                checked = settings.shareText,
            ) { v -> app.settings.update { it.withShareText(v) } }

            PreferenceCategory(stringResource(R.string.notifications))
            SwitchPreference(
                title = stringResource(R.string.pref_show_notification_title),
                summary = stringResource(R.string.pref_show_notification_summary),
                checked = settings.showNotification,
            ) { v -> app.settings.update { it.copy(showNotification = v) } }
            if (settings.showNotification && !notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PreferenceItem(
                    title = stringResource(R.string.notification_permission_denied_title),
                    summary = stringResource(R.string.notification_permission_denied_summary),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            SwitchPreference(
                title = stringResource(R.string.pref_auto_close_notification_title),
                summary = stringResource(R.string.pref_auto_close_notification_summary),
                checked = settings.autoCloseNotification,
                enabled = settings.showNotification,
            ) { v -> app.settings.update { it.copy(autoCloseNotification = v) } }
            val tapLabels = tapActionLabels()
            PreferenceItem(
                title = stringResource(R.string.notification_action),
                summary = tapLabels[settings.tapAction.ordinal],
                enabled = settings.showNotification,
                onClick = { dialog = SettingsDialog.TapAction },
            )

            PreferenceCategory(stringResource(R.string.category_about))
            PreferenceItem(
                title = stringResource(R.string.privacy_policy),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, ShareGyazoApp.PRIVACY_POLICY_URL.toUri()))
                },
            )
            PreferenceItem(title = stringResource(R.string.opensource_license), onClick = onOpenLicenses)
            val shareText = stringResource(R.string.share_app_text)
            PreferenceItem(
                title = "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                summary = stringResource(R.string.share_app_summary),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
        }
    }

    when (dialog) {
        SettingsDialog.DefaultProfile -> {
            val options = listOf(stringResource(R.string.default_profile_none)) + profiles.map { it.name }
            val selected = profiles.indexOfFirst { it.uuid == defaultUuid } + 1
            SingleChoiceDialog(
                title = stringResource(R.string.default_profile),
                options = options,
                selectedIndex = selected,
                onSelect = { index ->
                    app.profiles.setDefault(if (index == 0) "" else profiles[index - 1].uuid)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        SettingsDialog.TapAction -> SingleChoiceDialog(
            title = stringResource(R.string.notification_action),
            options = tapActionLabels(),
            selectedIndex = settings.tapAction.ordinal,
            onSelect = { index ->
                app.settings.update { it.copy(tapAction = TapAction.entries[index]) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun tapActionLabels(): List<String> = TapAction.entries.map {
    stringResource(
        when (it) {
            TapAction.NONE -> R.string.none
            TapAction.OPEN -> R.string.open_url
            TapAction.SHARE -> R.string.share_url
            TapAction.COPY -> R.string.copy_url
        },
    )
}
