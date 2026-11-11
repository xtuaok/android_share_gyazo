package xtuaok.sharegyazo.upload

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import xtuaok.sharegyazo.R
import xtuaok.sharegyazo.data.AppSettings
import xtuaok.sharegyazo.data.TapAction

object Notifications {
    /** Kept from the legacy app so the user's channel settings survive the update. */
    private const val CHANNEL_RESULT = "N_CH_ALL"
    private const val CHANNEL_PROGRESS = "upload_progress"

    private const val ID_DONE = 2
    private const val ID_FAILED = 3

    fun createChannels(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_RESULT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_name_all))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_PROGRESS, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.notification_name_progress))
                    .build(),
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun progress(context: Context, percent: Int?): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(
                if (percent == null) context.getString(R.string.convert_image)
                else context.getString(R.string.uploading_percent, percent),
            )
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    fun notify(context: Context, id: Int, notification: Notification, tag: String? = null) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(tag, id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun showDone(context: Context, url: String, thumbnail: Bitmap?, settings: AppSettings) {
        val base = url.hashCode() * 4
        val view = PendingIntent.getActivity(context, base, viewIntent(url), FLAGS)
        val share = PendingIntent.getActivity(context, base + 1, shareChooser(url), FLAGS)
        val copy = PendingIntent.getBroadcast(
            context, base + 2,
            Intent(context, CopyUrlReceiver::class.java).putExtra(Intent.EXTRA_TEXT, url),
            FLAGS,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setContentTitle(context.getString(R.string.message_uploaded))
            .setContentText(url)
            .setSmallIcon(R.drawable.ic_cloud_done_24dp)
            .setAutoCancel(settings.autoCloseNotification)
            .addAction(R.drawable.ic_open_in_browser_24dp, context.getString(R.string.open), view)
            .addAction(R.drawable.ic_share_24dp, context.getString(R.string.share), share)
            .addAction(R.drawable.ic_content_copy_24dp, context.getString(R.string.copy), copy)

        when (settings.tapAction) {
            TapAction.OPEN -> builder.setContentIntent(view)
            TapAction.SHARE -> builder.setContentIntent(share)
            TapAction.COPY -> builder.setContentIntent(copy)
            TapAction.NONE -> Unit
        }
        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(thumbnail)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(url),
                )
        }
        notify(context, ID_DONE, builder.build(), tag = url)
    }

    fun showFailed(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setContentTitle(context.getString(R.string.failed_to_upload))
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message + "\n" + context.getString(R.string.failed_to_upload_detail)),
            )
            .setSmallIcon(R.drawable.ic_warning_white_24dp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notify(context, ID_FAILED, notification)
    }

    fun viewIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, url.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun shareChooser(url: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
            null,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
