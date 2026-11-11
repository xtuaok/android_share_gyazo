package xtuaok.sharegyazo.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import xtuaok.sharegyazo.R
import xtuaok.sharegyazo.ui.components.BackButton
import xtuaok.sharegyazo.ui.components.PreferenceItem

private data class License(val name: String, val url: String, val license: String)

private const val APACHE_2 = "Apache License 2.0"

private val licenses = listOf(
    License("OkHttp / Okio", "https://square.github.io/okhttp/", APACHE_2),
    License("Kotlin / kotlinx.coroutines / kotlinx.serialization", "https://kotlinlang.org/", APACHE_2),
    License("Android Jetpack (AndroidX)", "https://developer.android.com/jetpack", APACHE_2),
    License("Material Icons", "https://fonts.google.com/icons", APACHE_2),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.opensource_license)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(licenses) { item ->
                PreferenceItem(title = item.name, summary = "${item.license}\n${item.url}") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, item.url.toUri()))
                }
            }
            item {
                PreferenceItem(
                    title = APACHE_2,
                    summary = "https://www.apache.org/licenses/LICENSE-2.0",
                ) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.apache.org/licenses/LICENSE-2.0".toUri()))
                }
            }
        }
    }
}
