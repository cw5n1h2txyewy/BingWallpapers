package io.github.bingwallpapers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Exercise real Android bitmap memory/disk reuse without fetching network images. */
public final class CacheChecks {
    private CacheChecks() {}

    /** Check actual tab clicks reuse page objects and do not issue another directory scan. */
    public static void runUi(Instrumentation instrumentation) throws InterruptedException {
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        click(instrumentation, activity, "已下载");
        awaitLibrary(instrumentation, activity);
        Object directoryPage = field(activity, "libraryPage");
        long scans = (Long) field(activity, "libraryRequest");
        long metadataAttempt = (Long) field(activity, "lastMetadataAttempt");
        click(instrumentation, activity, "最近八天");
        Object recentPage = field(activity, "recentPage");
        for (int index = 0; index < 8; index++) {
            click(instrumentation, activity, "已下载");
            require(field(activity, "libraryPage") == directoryPage, "Switch rebuilt the library page");
            click(instrumentation, activity, "最近八天");
            require(field(activity, "recentPage") == recentPage, "Switch rebuilt the recent page");
        }
        require((Long) field(activity, "libraryRequest") == scans, "Switch repeated the directory scan");
        require((Long) field(activity, "lastMetadataAttempt") == metadataAttempt, "Switch repeated the metadata request");
        click(instrumentation, activity, "已下载");
        click(instrumentation, activity, "刷新已下载");
        awaitLibrary(instrumentation, activity);
        require((Long) field(activity, "libraryRequest") == scans + 1, "Manual refresh did not rescan exactly once");
        require(field(activity, "libraryPage") != directoryPage, "Manual refresh kept the stale page");
    }

    /** Wait for asynchronous directory enumeration, with a fixed failure deadline. */
    private static void awaitLibrary(Instrumentation instrumentation, Activity activity) throws InterruptedException {
        long deadline = android.os.SystemClock.uptimeMillis() + 15000;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync();
            if (field(activity, "libraryPage") != null) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Directory load did not finish");
    }

    /** Perform a real button click on the Activity main thread. */
    private static void click(Instrumentation instrumentation, Activity activity, String text) {
        java.util.concurrent.atomic.AtomicBoolean clicked = new java.util.concurrent.atomic.AtomicBoolean();
        instrumentation.runOnMainSync(() -> {
            View view = find(activity.findViewById(android.R.id.content), text);
            if (view != null) clicked.set(view.performClick());
        });
        require(clicked.get(), "Cannot click " + text);
        instrumentation.waitForIdleSync();
    }

    /** Find a visible text action without depending on screen coordinates. */
    private static View find(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return view;
        if (view instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) {
            View match = find(group.getChildAt(index), text);
            if (match != null) return match;
        }
        return null;
    }

    /** Observe existing page/cache state without adding test-only production APIs. */
    private static Object field(Activity activity, String name) {
        try {
            java.lang.reflect.Field field = activity.getClass().getDeclaredField(name);
            field.setAccessible(true); return field.get(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    /** Verify warm memory, process-cold disk, corrupt-file recovery and disk budget. */
    public static void run(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), "cache-check-" + UUID.randomUUID());
        AtomicInteger loads = new AtomicInteger();
        ThumbnailCache.Loader loader = () -> {
            loads.incrementAndGet();
            Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.RED); return bitmap;
        };
        ThumbnailCache cache = new ThumbnailCache(directory, 1024 * 1024);
        try {
            Bitmap first = cache.get("photo", loader);
            require(cache.get("photo", loader) == first && loads.get() == 1, "Warm memory reloaded original");
            ThumbnailCache cold = new ThumbnailCache(directory, 1024 * 1024);
            require(cold.get("photo", loader).getWidth() == 16 && loads.get() == 1, "Cold cache did not reuse disk preview");
            File[] files = directory.listFiles();
            require(files != null && files.length == 1, "Preview was not saved atomically");
            try (FileOutputStream output = new FileOutputStream(files[0])) { output.write(new byte[]{1, 2, 3}); }
            ThumbnailCache repaired = new ThumbnailCache(directory, 1024 * 1024);
            repaired.get("photo", loader);
            require(loads.get() == 2, "Corrupt cache was not regenerated");
            repaired.clear();
            require(directory.listFiles() != null && directory.listFiles().length == 0, "Explicit cache clear failed");
            ThumbnailCache bounded = new ThumbnailCache(directory, 1000);
            for (int index = 0; index < 5; index++) bounded.get("photo-" + index, loader);
            long size = 0;
            for (File file : directory.listFiles()) size += file.length();
            require(size <= 1000, "Disk preview cache exceeded budget");
        } finally {
            cache.clear(); directory.delete();
        }
    }

    /** Fail instrumentation with the violated cache behavior. */
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
