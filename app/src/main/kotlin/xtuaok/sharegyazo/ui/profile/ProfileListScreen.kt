package xtuaok.sharegyazo.ui.profile

import androidx.compose.foundation.clickable
import xtuaok.sharegyazo.data.ProfileTemplate
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xtuaok.sharegyazo.R
import xtuaok.sharegyazo.app
import xtuaok.sharegyazo.data.Profile
import xtuaok.sharegyazo.data.ProfileKind
import xtuaok.sharegyazo.ui.components.BackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onBack: () -> Unit,
    onEdit: (uuid: String) -> Unit,
    onCreate: (ProfileTemplate) -> Unit,
) {
    var chooseTemplate by rememberSaveable { mutableStateOf(false) }
    val repo = LocalContext.current.app.profiles
    val profiles by repo.profiles.collectAsStateWithLifecycle()
    val defaultUuid by repo.defaultUuid.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_list_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { chooseTemplate = true }) {
                Icon(painterResource(R.drawable.ic_add_white_24dp), stringResource(R.string.profile_setting_title))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(profiles, key = { it.uuid }) { profile ->
                val badges = listOfNotNull(
                    stringResource(R.string.builtin_badge).takeIf { profile.isBuiltIn },
                    stringResource(R.string.default_badge).takeIf { profile.uuid == defaultUuid },
                )
                ListItem(
                    headlineContent = { Text(profile.name) },
                    supportingContent = { Text(profileSummary(profile)) },
                    trailingContent = if (badges.isNotEmpty()) {
                        {
                            Text(
                                badges.joinToString(" · "),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else null,
                    modifier = Modifier.clickable { onEdit(profile.uuid) },
                )
            }
        }
    }

    if (chooseTemplate) {
        TemplateDialog(
            onSelect = { template ->
                chooseTemplate = false
                onCreate(template)
            },
            onDismiss = { chooseTemplate = false },
        )
    }
}

@Composable
private fun TemplateDialog(onSelect: (ProfileTemplate) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Triple(ProfileTemplate.GYAZO_ANONYMOUS, R.string.template_gyazo_anonymous, R.string.template_gyazo_anonymous_summary),
        Triple(ProfileTemplate.IMGUR_ANONYMOUS, R.string.template_imgur_anonymous, R.string.template_imgur_anonymous_summary),
        Triple(ProfileTemplate.BLANK, R.string.template_blank, R.string.template_blank_summary),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_dialog_title)) },
        text = {
            Column {
                options.forEach { (template, title, summary) ->
                    ListItem(
                        headlineContent = { Text(stringResource(title)) },
                        supportingContent = { Text(stringResource(summary)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSelect(template) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun profileSummary(profile: Profile): String = when (profile.kind) {
    ProfileKind.GYAZO, ProfileKind.IMGUR ->
        stringResource(if (profile.isLoggedIn) R.string.logged_in else R.string.not_logged_in)
    ProfileKind.CUSTOM ->
        if (profile.isImgurAnonymous) stringResource(R.string.imgur_anonymous_summary) else profile.url
}
