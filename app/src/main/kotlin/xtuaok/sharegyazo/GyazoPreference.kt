package xtuaok.sharegyazo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import xtuaok.sharegyazo.share.ShareShortcuts
import xtuaok.sharegyazo.ui.profile.ProfileEditScreen
import xtuaok.sharegyazo.ui.profile.ProfileListScreen
import xtuaok.sharegyazo.ui.settings.LicensesScreen
import xtuaok.sharegyazo.ui.settings.SettingsScreen
import xtuaok.sharegyazo.ui.theme.ShareGyazoTheme

@Serializable private data object SettingsRoute
@Serializable private data object ProfilesRoute
@Serializable private data class ProfileEditRoute(val uuid: String? = null)
@Serializable private data object LicensesRoute

/**
 * Launcher activity: settings and profile management.
 * The class name is kept from the legacy app so existing home-screen icons keep working.
 */
class GyazoPreference : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // Make sure Direct Share targets exist, e.g. right after updating from the legacy app.
            ShareShortcuts.publish(this, app.profiles.profiles.value)
        }

        setContent {
            ShareGyazoTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = SettingsRoute) {
                    composable<SettingsRoute> {
                        SettingsScreen(
                            onOpenProfiles = { nav.navigate(ProfilesRoute) },
                            onOpenLicenses = { nav.navigate(LicensesRoute) },
                        )
                    }
                    composable<ProfilesRoute> {
                        ProfileListScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { uuid -> nav.navigate(ProfileEditRoute(uuid)) },
                        )
                    }
                    composable<ProfileEditRoute> { entry ->
                        ProfileEditScreen(
                            uuid = entry.toRoute<ProfileEditRoute>().uuid,
                            onDone = { nav.popBackStack() },
                        )
                    }
                    composable<LicensesRoute> {
                        LicensesScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
