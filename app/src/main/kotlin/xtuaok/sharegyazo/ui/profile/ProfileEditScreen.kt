package xtuaok.sharegyazo.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xtuaok.sharegyazo.R
import xtuaok.sharegyazo.app
import xtuaok.sharegyazo.data.Profile
import xtuaok.sharegyazo.data.ProfileCodec
import xtuaok.sharegyazo.ui.components.BackButton
import xtuaok.sharegyazo.ui.components.PreferenceCategory
import xtuaok.sharegyazo.ui.components.ResourceIconButton
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(uuid: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.profiles
    val original = remember(uuid) { repo.get(uuid) }
    val isNew = original == null
    val profiles by repo.profiles.collectAsStateWithLifecycle()

    // Saved as JSON so edits survive configuration changes.
    var profileJson by rememberSaveable {
        mutableStateOf(ProfileCodec.encode(original ?: Profile.newGyazo("gyazo")))
    }
    val profile = ProfileCodec.decodeOrNull(profileJson) ?: return
    fun edit(transform: (Profile) -> Profile) {
        profileJson = ProfileCodec.encode(transform(profile))
    }

    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val nameValid = profile.name.isNotBlank()
    val urlValid = Profile.isValidUrl(profile.url)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) stringResource(R.string.profile_setting_title) else original.name) },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (!isNew) {
                        ResourceIconButton(
                            R.drawable.ic_delete_white_24dp,
                            stringResource(R.string.profile_menu_delete_title),
                        ) { confirmRemove = true }
                    }
                    ResourceIconButton(
                        R.drawable.ic_save_white_24dp,
                        stringResource(R.string.profile_menu_save_title),
                    ) {
                        if (nameValid && urlValid) {
                            repo.save(profile.copy(name = profile.name.trim(), url = profile.url.trim()))
                            Toast.makeText(context, R.string.toast_save_profile, Toast.LENGTH_SHORT).show()
                            onDone()
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreferenceCategory(stringResource(R.string.profile_name_title))
            OutlinedTextField(
                value = profile.name,
                onValueChange = { name ->
                    edit { p -> p.copy(name = name, url = Profile.presetUrlFor(name) ?: p.url) }
                },
                label = { Text(stringResource(R.string.rename_dialog_hint)) },
                isError = !nameValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            PreferenceCategory(stringResource(R.string.profile_server_settings))
            OutlinedTextField(
                value = profile.url,
                onValueChange = { url -> edit { it.copy(url = url) } },
                label = { Text(stringResource(R.string.cgi_url_dialog_title)) },
                placeholder = { Text(Profile.GYAZO_URL) },
                supportingText = if (!urlValid) {
                    { Text(stringResource(R.string.cgi_url_invalid)) }
                } else null,
                isError = !urlValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = profile.gyazoId,
                onValueChange = { id -> edit { it.copy(gyazoId = id.trim()) } },
                label = { Text(stringResource(R.string.gyazo_id_dialog_title)) },
                supportingText = { Text(stringResource(R.string.gyazo_id_dialog_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PreferenceCategory(stringResource(R.string.profile_image_settings))
            Text(stringResource(R.string.image_format), style = MaterialTheme.typography.bodyLarge)
            val formats = listOf(Profile.FORMAT_PNG to "PNG", Profile.FORMAT_JPEG to "JPEG")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                formats.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = profile.format == value,
                        onClick = { edit { it.copy(format = value) } },
                        shape = SegmentedButtonDefaults.itemShape(index, formats.size),
                    ) { Text(label) }
                }
            }
            Text(
                stringResource(R.string.image_quality_dialog_message, profile.quality),
                style = MaterialTheme.typography.bodyLarge,
                color = if (profile.isPng) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
            Slider(
                value = profile.quality.toFloat(),
                onValueChange = { v -> edit { it.copy(quality = v.roundToInt()) } },
                valueRange = 0f..100f,
                enabled = !profile.isPng,
            )
        }
    }

    if (confirmRemove && original != null) {
        val canRemove = profiles.size > 1
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_profile_title)) },
            text = {
                Text(
                    if (canRemove) stringResource(R.string.remove_confirm_message, original.name)
                    else stringResource(R.string.cannot_remove_profile),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    if (canRemove && repo.remove(original.uuid)) {
                        Toast.makeText(context, R.string.toast_remove_profile, Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = if (canRemove) {
                { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } }
            } else null,
        )
    }
}
