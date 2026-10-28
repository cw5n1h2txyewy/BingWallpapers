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
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
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
                try (Cursor row = getTargetContext().getContentResolver().query(saved.uri(),
                        new String[]{"relative_path", "is_pending", "_size", "_display_name"}, null, null, null)) {
                    check(row != null && row.moveToFirst(), "Saved photo missing");
                    check(ImageStore.DIRECTORY.equals(row.getString(0)), "Wrong directory");
                    check(row.getInt(1) == 0 && row.getLong(2) > 0, "Photo not published");
                    check(saved.name().equals(row.getString(3)), "Filename changed");
                }
                ImageStore.Saved repeated = store.save(first, size);
                check(repeated.existing() && repeated.uri().equals(saved.uri()), "Repeat created a duplicate");
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
