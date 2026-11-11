package xtuaok.sharegyazo.upload

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xtuaok.sharegyazo.R
import xtuaok.sharegyazo.app
import xtuaok.sharegyazo.data.Profile
import xtuaok.sharegyazo.data.ProfileCodec
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Converts and uploads one image, then copies the URL and posts the result notification.
 * Replaces the legacy IntentService.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val foregroundId = id.hashCode()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = ctx.app.settings.settings.value
        val profile = inputData.getString(KEY_PROFILE)?.let(ProfileCodec::decodeOrNull)
        val source = inputData.getString(KEY_FILE)?.let(::File)
        if (profile == null || source == null || !source.exists()) {
            return Result.failure(workDataOf(KEY_ERROR to "Invalid request"))
        }

        if (settings.showNotification) {
            runCatching { setForeground(foregroundInfo(null)) }
                .onFailure { Log.w(TAG, "Could not run in foreground", it) }
        }

        var willRetry = false
        try {
            val converted = try {
                withContext(Dispatchers.IO) { ImageConverter.convert(source, profile, ctx.cacheDir) }
            } catch (e: Exception) {
                return fail("${ctx.getString(R.string.error_convert)}: ${e.message}", e)
            } catch (e: OutOfMemoryError) {
                return fail("Out of memory", e)
            }

            try {
                val endpoint = UploadEndpoint.forProfileUrl(profile.url)
                val response = try {
                    withContext(Dispatchers.IO) {
                        Uploader().upload(endpoint, converted.file, converted.mimeType, profile.gyazoId) { percent ->
                            setProgressAsync(workDataOf(KEY_PROGRESS to percent))
                            if (settings.showNotification) {
                                Notifications.notify(ctx, foregroundId, Notifications.progress(ctx, percent))
                            }
                        }
                    }
                } catch (e: IOException) {
                    if (runAttemptCount < MAX_RETRY) {
                        Log.w(TAG, "Upload failed, will retry", e)
                        willRetry = true
                        return Result.retry()
                    }
                    return fail("${ctx.getString(R.string.error_network)}: ${e.message}", e)
                }

                response.gyazoId?.let { ctx.app.profiles.updateGyazoId(profile.uuid, it) }

                val url = UploadProtocol.resolveImageUrl(endpoint, response.body, profile.format)
                if (!response.isSuccessful || url == null) {
                    Log.w(TAG, "Unexpected response ${response.code}: ${response.body}")
                    return fail("HTTP ${response.code}")
                }

                if (settings.copyUrl) {
                    withContext(Dispatchers.Main) { CopyUrlReceiver.copyToClipboard(ctx, url) }
                }
                if (settings.showNotification) {
                    Notifications.showDone(ctx, url, converted.thumbnail, settings)
                }
                return Result.success(workDataOf(KEY_URL to url))
            } finally {
                converted.file.delete()
                converted.thumbnail?.recycle()
            }
        } finally {
            if (!willRetry) source.delete()
        }
    }

    private suspend fun fail(message: String, cause: Throwable? = null): Result {
        Log.e(TAG, message, cause)
        val ctx = applicationContext
        withContext(Dispatchers.Main) {
            Toast.makeText(ctx, ctx.getString(R.string.failed_to_upload), Toast.LENGTH_LONG).show()
        }
        if (ctx.app.settings.settings.value.showNotification) {
            Notifications.showFailed(ctx, message)
        }
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    private fun foregroundInfo(percent: Int?): ForegroundInfo {
        val notification = Notifications.progress(applicationContext, percent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(foregroundId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(foregroundId, notification)
        }
    }

    companion object {
        private const val TAG = "UploadWorker"
        private const val MAX_RETRY = 3

        private const val KEY_PROFILE = "profile"
        private const val KEY_FILE = "file"
        const val KEY_PROGRESS = "progress"
        const val KEY_URL = "url"
        const val KEY_ERROR = "error"

        fun enqueue(context: Context, profile: Profile, file: File): UUID {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    workDataOf(KEY_PROFILE to ProfileCodec.encode(profile), KEY_FILE to file.absolutePath),
                )
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }
    }
}
