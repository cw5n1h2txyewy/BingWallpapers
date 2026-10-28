package io.github.bingwallpapers;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;
import org.json.*;

/** Real Bing/MediaStore integration checks; keeps the requested photos for inspection. */
public final class DeviceChecks extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (arguments != null && arguments.containsKey("cache")) {
                CacheChecks.run(getTargetContext());
                result.putString("stream", "PASS: warm memory reuse, cold disk reuse, corrupt preview recovery, explicit clear and bounded disk size\n");
                finish(Activity.RESULT_OK, result); return;
            }
            if (arguments != null && arguments.containsKey("cache_ui")) {
                CacheChecks.runUi(this);
                result.putString("stream", "PASS: eight repeated tab switches reused both pages; no extra directory scan or metadata request; manual refresh rescanned exactly once\n");
                finish(Activity.RESULT_OK, result); return;
            }
            if (arguments != null && arguments.containsKey("gallery")) {
                boolean expected = arguments.getString("gallery").equals("visible");
                List<ImageStore.Saved> saved = new ImageStore(getTargetContext()).list();
                String expectedName = arguments.getString("filename", "codex-legacy-gallery-check.jpg");
                ImageStore.Saved fixture = saved.stream().filter(item -> item.name().equals(expectedName)).findFirst().orElse(null);
                check((fixture != null) == expected, "Legacy photo visibility: expected " + expected + ", actual " + (fixture != null));
                check(saved.stream().noneMatch(item -> item.name().equals("codex-outside-gallery-check.jpg")), "Photo outside BingWallpapers leaked into gallery");
                if (fixture != null) {
                    android.graphics.Bitmap thumbnail = new ImageStore(getTargetContext()).thumbnail(fixture.uri());
                    check(thumbnail != null, "Cannot read legacy thumbnail");
                    thumbnail.recycle();
                }
                result.putString("stream", "PASS: legacy gallery " + (expected ? "visible with readable thumbnail" : "not visible") + "; unrelated filename excluded; matching photos=" + saved.size() + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            List<Wallpaper> images = BingClient.parse(BingClient.metadata("zh-CN"));
            check(images.size() > 0, "No Bing images");
            Wallpaper first = images.get(0);
            check(new Wallpaper(first.toJson()).date.equals(first.date), "Date did not round-trip");
            try {
                new Wallpaper(new JSONObject().put("startdate", "20261028").put("copyright", "test").put("urlbase", "https://invalid.example"));
                throw new AssertionError("Unsafe URL accepted");
            } catch (JSONException expected) { /* Host boundary must reject external input. */ }
            ImageStore store = new ImageStore(getTargetContext());
            for (Resolution size : Resolution.values()) {
                ImageStore.Saved saved = store.save(first, size);
                if (android.provider.DocumentsContract.isDocumentUri(getTargetContext(), saved.uri())) {
                    try (InputStream input = getTargetContext().getContentResolver().openInputStream(saved.uri())) {
                        check(input != null && input.read() != -1, "Directory photo is empty");
                    }
                    check(WallpaperFiles.matches(saved.name()), "Unexpected directory photo name");
                } else try (Cursor row = getTargetContext().getContentResolver().query(saved.uri(),
                        new String[]{"relative_path", "is_pending", "_size", "_display_name"}, null, null, null)) {
                    check(row != null && row.moveToFirst(), "Saved photo missing");
                    check(ImageStore.DIRECTORY.equals(row.getString(0)), "Wrong directory");
                    check(row.getInt(1) == 0 && row.getLong(2) > 0, "Photo not published");
                    check(saved.name().equals(row.getString(3)), "Filename changed");
                }
                int beforeRepeat = store.list().size();
                ImageStore.Saved repeated = store.save(first, size);
                check(repeated.existing() && repeated.name().equals(saved.name()) && store.list().size() == beforeRepeat,
                        "Repeat created a duplicate");
                Bundle progress = new Bundle(); progress.putString("stream", "PASS " + size.name() + " " + saved.name() + "\n"); sendStatus(0, progress);
            }
            int before = store.list().size();
            File invalid = File.createTempFile("invalid", ".jpg", getTargetContext().getCacheDir());
            try {
                try (FileOutputStream out = new FileOutputStream(invalid)) { out.write("not a JPEG".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                try { store.publish(invalid, first.date, Resolution.FHD); throw new AssertionError("Invalid JPEG accepted"); }
                catch (IOException expected) { /* An invalid response must leave no MediaStore row. */ }
                check(store.list().size() == before, "Invalid response left a photo row");
            } finally { invalid.delete(); }
            result.putString("stream", "PASS: live resolutions, actual filenames, repeat downloads, pending publication, malformed data, date round-trip and URL validation\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError error) {
            result.putString("stream", "FAIL: " + error + "\n"); finish(Activity.RESULT_CANCELED, result);
        }
    }
}
