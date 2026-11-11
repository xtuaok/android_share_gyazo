package xtuaok.sharegyazo.ui.profile

import androidx.compose.foundation.clickable
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
    onEdit: (uuid: String?) -> Unit,
) {
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
            FloatingActionButton(onClick = { onEdit(null) }) {
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
}

@Composable
private fun profileSummary(profile: Profile): String = when (profile.kind) {
    ProfileKind.GYAZO -> stringResource(
        if (profile.isLoggedIn) R.string.gyazo_logged_in else R.string.gyazo_not_logged_in,
    )
    ProfileKind.IMGUR -> stringResource(
        if (profile.isLoggedIn) R.string.imgur_summary_account else R.string.imgur_summary_anonymous,
    )
    ProfileKind.CUSTOM -> profile.url
}
