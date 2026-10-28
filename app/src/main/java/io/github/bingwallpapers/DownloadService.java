package io.github.bingwallpapers;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import org.json.*;

/** Serial foreground download queue independent of Activity recreation. */
public final class DownloadService extends Service {
    public static final String ACTION = "io.github.bingwallpapers.DOWNLOAD_STATUS";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final String CHANNEL = "downloads";

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "图片下载", NotificationManager.IMPORTANCE_LOW));
    }

    private Notification notification(String message, boolean ongoing) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("必应美图").setContentText(message).setContentIntent(open).setOngoing(ongoing).build();
    }

    private void report(String message, boolean complete) {
        getSharedPreferences("bing", MODE_PRIVATE).edit().putString("status", message).apply();
        sendBroadcast(new Intent(ACTION).setPackage(getPackageName()).putExtra("message", message).putExtra("complete", complete));
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(1, notification(message, true));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification("正在准备下载…", true));
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
                    report("正在下载 " + (i + 1) + "/" + array.length() + " · " + resolution.label, false);
                    try {
                        ImageStore.Saved result = new ImageStore(this).save(new Wallpaper(array.getJSONObject(i)), resolution);
                        if (result.existing()) skipped++; else success++;
                    } catch (java.io.IOException | JSONException | RuntimeException error) {
                        failed++; lastError = error.getMessage();
                    }
                }
            } catch (JSONException | RuntimeException error) { failed++; lastError = error.getMessage(); }
            String result = "下载完成：保存 " + success + "，已存在 " + skipped + "，失败 " + failed;
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
        report("下载超时，请重新打开应用重试", true);
        worker.shutdownNow(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
