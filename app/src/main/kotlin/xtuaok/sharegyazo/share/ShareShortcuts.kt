package xtuaok.sharegyazo.share

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import xtuaok.sharegyazo.R
import xtuaok.sharegyazo.ShareGyazo
import xtuaok.sharegyazo.data.Profile

/**
 * Publishes each profile as a Direct Share target in the system share sheet
 * (replacement for the removed ChooserTargetService).
 */
object ShareShortcuts {
    private const val TAG = "ShareShortcuts"
    const val CATEGORY = "xtuaok.sharegyazo.category.PROFILE_SHARE_TARGET"

    fun publish(context: Context, profiles: List<Profile>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        val icon = IconCompat.createWithResource(context, R.drawable.ic_share_upload)
        val shortcuts = profiles
            .sortedByDescending { it.useCount }
            .take(max)
            .mapIndexed { rank, profile ->
                ShortcutInfoCompat.Builder(context, profile.uuid)
                    .setShortLabel(profile.name)
                    .setIcon(icon)
                    .setIntent(Intent(context, ShareGyazo::class.java).setAction(Intent.ACTION_DEFAULT))
                    .setCategories(setOf(CATEGORY))
                    .setLongLived(true)
                    .setRank(rank)
                    .build()
            }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: RuntimeException) {
            // Rate limited or otherwise rejected by the launcher; not fatal.
            Log.w(TAG, "Failed to publish share shortcuts", e)
        }
    }

    fun reportUsed(context: Context, profile: Profile) {
        ShortcutManagerCompat.reportShortcutUsed(context, profile.uuid)
    }
}
