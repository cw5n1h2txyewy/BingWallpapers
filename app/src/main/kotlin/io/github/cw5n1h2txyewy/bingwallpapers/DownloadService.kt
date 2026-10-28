package io.github.cw5n1h2txyewy.bingwallpapers

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import java.io.IOException
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONException

/** Serial foreground download queue independent of Activity recreation. */
class DownloadService : Service() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppSettings.wrap(base))

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, AppSettings.text(this, R.string.download_channel), NotificationManager.IMPORTANCE_LOW))
    }

    /** Animate only active downloads; terminal notifications use a static, dismissible icon. */
    private fun notification(message: String, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (ongoing) android.R.drawable.stat_sys_download else R.drawable.ic_download_done)
            .setContentTitle(AppSettings.text(this, R.string.app_name)).setContentText(message).setContentIntent(open)
            .setOngoing(ongoing).setAutoCancel(!ongoing).setOnlyAlertOnce(true)
            .setCategory(if (ongoing) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STATUS)
            .setProgress(0, 0, false).build()
    }

    /** Publish a localized status to persistent storage, the interface and the foreground notification. */
    private fun report(message: String, complete: Boolean) {
        getSharedPreferences("bing", MODE_PRIVATE).edit().putString("status", message)
            .putString("status-language", AppSettings.wrap(applicationContext).resources.configuration.locales[0].toLanguageTag()).apply()
        sendBroadcast(Intent(ACTION).setPackage(packageName).putExtra("message", message).putExtra("complete", complete))
        if (canNotify()) getSystemService(NotificationManager::class.java).notify(1, notification(message, true))
    }

    /** Check the runtime notification permission without changing the active download queue. */
    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notification(AppSettings.text(this, R.string.preparing), true))
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val json = intent.getStringExtra("images")
        val size = intent.getStringExtra("resolution")
        worker.execute {
            var success = 0
            var skipped = 0
            var failed = 0
            var lastError = ""
            try {
                val array = JSONArray(requireNotNull(json) { "Missing images" })
                val resolution = Resolution.valueOf(requireNotNull(size) { "Missing resolution" })
                for (i in 0 until array.length()) {
                    report(AppSettings.text(this, R.string.download_progress, i + 1, array.length(),
                        resolution.label(AppSettings.wrap(applicationContext))), false)
                    try {
                        val result = ImageStore(this).save(Wallpaper(array.getJSONObject(i)), resolution)
                        if (result.existing) skipped++ else success++
                    } catch (error: Exception) {
                        if (error !is IOException && error !is JSONException && error !is RuntimeException) throw error
                        failed++
                        lastError = error.message.orEmpty()
                    }
                }
            } catch (error: Exception) {
                if (error !is JSONException && error !is RuntimeException) throw error
                failed++
                lastError = error.message.orEmpty()
            }
            var result = AppSettings.text(this, R.string.download_result, success, skipped, failed)
            if (failed > 0) result += "\n$lastError"
            report(result, true)
            // A newer queued start must keep the service and its foreground notification alive.
            if (stopSelfResult(startId)) {
                stopForeground(STOP_FOREGROUND_DETACH)
                if (canNotify()) getSystemService(NotificationManager::class.java).notify(1, notification(result, false))
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        report(AppSettings.text(this, R.string.download_timeout), true)
        worker.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION = "io.github.cw5n1h2txyewy.bingwallpapers.DOWNLOAD_STATUS"
        private const val CHANNEL = "downloads"
    }
}
