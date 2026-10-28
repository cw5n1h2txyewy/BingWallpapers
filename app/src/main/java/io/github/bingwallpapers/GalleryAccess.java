package io.github.bingwallpapers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Version-aware read access for photos created by other applications. */
public final class GalleryAccess {
    private GalleryAccess() {}

    /** Return whether the app can query all indexed images in its download directory. */
    public static boolean isFull(Context context) {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Distinguish Android 14+ selected-photo access from complete directory access. */
    public static boolean isPartial(Context context) {
        return Build.VERSION.SDK_INT >= 34 && !isFull(context)
                && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
    }

    /** Request image read access and explicit selected-photo handling on supported releases. */
    public static String[] permissions() {
        if (Build.VERSION.SDK_INT >= 34) return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        if (Build.VERSION.SDK_INT >= 33) return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }
}
