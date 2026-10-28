package io.github.cw5n1h2txyewy.bingwallpapers;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import org.json.*;

/** Serial foreground download queue independent of Activity recreation. */
public final class DownloadService extends Service {
    public static final String ACTION = "io.github.cw5n1h2txyewy.bingwallpapers.DOWNLOAD_STATUS";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final String CHANNEL = "downloads";

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppSettings.wrap(base)); }

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, AppSettings.text(this, R.string.download_channel), NotificationManager.IMPORTANCE_LOW));
    }

    /** Animate only active downloads; terminal notifications use a static, dismissible icon. */
    private Notification notification(String message, boolean ongoing) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(ongoing ? android.R.drawable.stat_sys_download : R.drawable.ic_download_done)
                .setContentTitle(AppSettings.text(this, R.string.app_name)).setContentText(message).setContentIntent(open)
                .setOngoing(ongoing).setAutoCancel(!ongoing).setOnlyAlertOnce(true)
                .setCategory(ongoing ? Notification.CATEGORY_PROGRESS : Notification.CATEGORY_STATUS)
                .setProgress(0, 0, false).build();
    }

    private void report(String message, boolean complete) {
        getSharedPreferences("bing", MODE_PRIVATE).edit().putString("status", message)
                .putString("status-language", AppSettings.wrap(getApplicationContext()).getResources().getConfiguration().getLocales().get(0).toLanguageTag()).apply();
        sendBroadcast(new Intent(ACTION).setPackage(getPackageName()).putExtra("message", message).putExtra("complete", complete));
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(1, notification(message, true));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification(AppSettings.text(this, R.string.preparing), true));
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }
        String json = intent.getStringExtra("images");
        String size = intent.getStringExtra("resolution");
        worker.execute(() -> {
            int success = 0, skipped = 0, failed = 0;
            String lastError = "";
            try {
                JSONArray array = new JSONArray(json);
                Resolution resolution = Resolution.valueOf(size);
                for (int i = 0; i < array.length(); i++) {
                    report(AppSettings.text(this, R.string.download_progress, i + 1, array.length(), resolution.label(AppSettings.wrap(getApplicationContext()))), false);
                    try {
                        ImageStore.Saved result = new ImageStore(this).save(new Wallpaper(array.getJSONObject(i)), resolution);
                        if (result.existing()) skipped++; else success++;
                    } catch (java.io.IOException | JSONException | RuntimeException error) {
                        failed++; lastError = error.getMessage();
                    }
                }
            } catch (JSONException | RuntimeException error) { failed++; lastError = error.getMessage(); }
            String result = AppSettings.text(this, R.string.download_result, success, skipped, failed);
            if (failed > 0) result += "\n" + lastError;
            report(result, true);
            // A newer queued start must keep the service and its foreground notification alive.
            if (stopSelfResult(startId)) {
                stopForeground(STOP_FOREGROUND_DETACH);
                if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    getSystemService(NotificationManager.class).notify(1, notification(result, false));
            }
        });
        return START_NOT_STICKY;
    }

    @Override public void onTimeout(int startId, int fgsType) {
        report(AppSettings.text(this, R.string.download_timeout), true);
        worker.shutdownNow(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
