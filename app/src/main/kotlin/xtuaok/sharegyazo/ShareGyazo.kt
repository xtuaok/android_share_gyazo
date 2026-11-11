package xtuaok.sharegyazo

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xtuaok.sharegyazo.data.Profile
import xtuaok.sharegyazo.share.ShareUiState
import xtuaok.sharegyazo.share.ShareViewModel
import xtuaok.sharegyazo.ui.theme.ShareGyazoTheme
import xtuaok.sharegyazo.upload.Notifications

/**
 * Receives images shared from other apps and starts the upload.
 * The class name is kept from the legacy app for compatibility.
 */
class ShareGyazo : ComponentActivity() {
    private val viewModel: ShareViewModel by viewModels()

    private var askingPermission = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            askingPermission = false
            viewModel.begin(intent)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askingPermission = savedInstanceState?.getBoolean(KEY_ASKING_PERMISSION) == true
        // Also runs after process death; begin() ignores repeated calls. While the permission
        // dialog is open, its result callback starts the flow instead.
        if (!askingPermission) {
            if (savedInstanceState == null && shouldAskNotificationPermission()) {
                askingPermission = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.begin(intent)
            }
        }

        setContent {
            ShareGyazoTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ShareContent(
                    state = state,
                    onChoose = viewModel::onProfileChosen,
                    onDismiss = viewModel::finish,
                )
                LaunchedEffect(state) {
                    when (val s = state) {
                        is ShareUiState.Completed -> {
                            launchResult(s)
                            finish()
                        }
                        is ShareUiState.NeedsLogin -> {
                            Toast.makeText(
                                this@ShareGyazo,
                                getString(R.string.login_required, s.serviceName),
                                Toast.LENGTH_LONG,
                            ).show()
                            startActivity(Intent(this@ShareGyazo, GyazoPreference::class.java))
                            finish()
                        }
                        ShareUiState.Finished -> finish()
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ASKING_PERMISSION, askingPermission)
    }

    private fun shouldAskNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.settings.settings.value.showNotification &&
            !Notifications.canPost(this)

    private fun launchResult(result: ShareUiState.Completed) {
        val intent = if (result.openBrowser) Notifications.viewIntent(result.url)
        else Notifications.shareChooser(result.url)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Nothing can handle it; the URL is still in the notification.
        }
    }

    private companion object {
        const val KEY_ASKING_PERMISSION = "asking_permission"
    }
}

@Composable
private fun ShareContent(
    state: ShareUiState,
    onChoose: (Profile, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is ShareUiState.ChooseProfile -> ProfileChooserDialog(state, onChoose, onDismiss)
        is ShareUiState.Uploading -> UploadingDialog(state.percent, onDismiss)
        else -> Unit
    }
}

@Composable
private fun ProfileChooserDialog(
    state: ShareUiState.ChooseProfile,
    onChoose: (Profile, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(state) { mutableStateOf(state.initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_profile)) },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                items(state.profiles, key = { it.uuid }) { profile ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = profile.uuid == selected.uuid,
                                onClick = { selected = profile },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = profile.uuid == selected.uuid, onClick = null)
                        Text(profile.name, Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(selected, true) }) { Text(stringResource(R.string.select_always)) }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(selected, false) }) { Text(stringResource(R.string.select_just_once)) }
        },
    )
}

@Composable
private fun UploadingDialog(percent: Int?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column {
                Text(
                    if (percent == null) stringResource(R.string.dialog_message_uploading)
                    else stringResource(R.string.uploading_percent, percent),
                )
                if (percent == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.continue_in_background)) }
        },
    )
}
