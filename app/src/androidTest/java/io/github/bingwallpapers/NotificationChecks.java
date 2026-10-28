package io.github.bingwallpapers;

import android.app.*;
import android.content.*;
import android.graphics.drawable.Animatable;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

/** Exercise the real service terminal notification without network or photo changes. */
public final class NotificationChecks {
    private NotificationChecks() {}

    /** Verify successful, failed and queued completions stop their animated status icon. */
    public static void run(Instrumentation instrumentation) throws InterruptedException {
        Context context = instrumentation.getTargetContext();
        instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        require(manager.areNotificationsEnabled(), "Enable download notifications before running this check");
        for (String payload : new String[]{"[]", "invalid-json"}) {
            manager.cancel(1);
            instrumentation.runOnMainSync(() -> context.startForegroundService(new Intent(context, DownloadService.class)
                    .putExtra("images", payload).putExtra("resolution", "FHD")));
            verifyTerminal(context, awaitTerminal(manager));
        }
        manager.cancel(1);
        instrumentation.runOnMainSync(() -> {
            for (int index = 0; index < 2; index++) context.startForegroundService(new Intent(context, DownloadService.class)
                    .putExtra("images", "[]").putExtra("resolution", "FHD"));
        });
        verifyTerminal(context, awaitTerminal(manager));
    }

    /** Wait for the real service to detach foreground/ongoing state from its notification. */
    private static Notification awaitTerminal(NotificationManager manager) throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            for (StatusBarNotification active : manager.getActiveNotifications()) {
                Notification notification = active.getNotification();
                if (active.getId() == 1 && (notification.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) == 0)
                    return notification;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Download notification never left foreground/ongoing state");
    }

    /** Match the reported symptom: finished text must not retain an animatable small icon. */
    private static void verifyTerminal(Context context, Notification notification) {
        require(notification.getSmallIcon().getResId() != android.R.drawable.stat_sys_download,
                "Finished notification still uses animated stat_sys_download");
        require(!(notification.getSmallIcon().loadDrawable(context) instanceof Animatable), "Finished icon is animated");
        require((notification.flags & Notification.FLAG_AUTO_CANCEL) != 0, "Finished notification should dismiss when opened");
        require((notification.flags & Notification.FLAG_NO_CLEAR) == 0, "Finished notification cannot be cleared");
        require(!notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE), "Indeterminate progress remains");
    }

    /** Fail the instrumentation with the violated notification contract. */
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
