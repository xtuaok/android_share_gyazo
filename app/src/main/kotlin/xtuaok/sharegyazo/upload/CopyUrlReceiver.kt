package xtuaok.sharegyazo.upload

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.getSystemService
import xtuaok.sharegyazo.R

/** "Copy" action of the upload notification. */
class CopyUrlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        copyToClipboard(context, url)
    }

    companion object {
        fun copyToClipboard(context: Context, url: String) {
            context.getSystemService<ClipboardManager>()
                ?.setPrimaryClip(ClipData.newPlainText("url text", url))
            Toast.makeText(context, R.string.toast_copy_url, Toast.LENGTH_SHORT).show()
        }
    }
}
