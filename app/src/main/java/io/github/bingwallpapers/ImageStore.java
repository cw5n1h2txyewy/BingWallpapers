package io.github.bingwallpapers;

import android.content.*;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

/** Publishes validated JPEG originals atomically to the system photo library. */
public final class ImageStore {
    public static final String DIRECTORY = "DCIM/BingWallpapers/";
    private final Context context;
    public ImageStore(Context context) { this.context = context.getApplicationContext(); }

    public record Saved(Uri uri, String name, boolean existing) {}

    public Saved save(Wallpaper image, Resolution resolution) throws IOException {
        File temporary = File.createTempFile("bing-", ".jpg", context.getCacheDir());
        try {
            try (OutputStream output = new FileOutputStream(temporary)) {
                BingClient.fetch(image.url(resolution.suffix), output, 40 * 1024 * 1024);
            }
            return publish(temporary, image.date, resolution);
        } finally { if (!temporary.delete()) temporary.deleteOnExit(); }
    }

    Saved publish(File file, String date, Resolution resolution) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (!"image/jpeg".equals(bounds.outMimeType) || !resolution.accepts(bounds.outWidth, bounds.outHeight)) {
            throw new IOException("Bing 未提供 " + resolution.label + "，实际为 " + bounds.outWidth + "×" + bounds.outHeight + "；未保存，请选择其他尺寸");
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (input.length() < 4) throw new IOException("图片为空");
            input.seek(input.length() - 2);
            if (input.readUnsignedShort() != 0xffd9) throw new IOException("JPEG 下载不完整，请重试");
        }
        String name = resolution.filename(date, bounds.outWidth, bounds.outHeight);
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        try (Cursor cursor = resolver.query(collection, new String[]{MediaStore.Images.Media._ID},
                "relative_path=? AND _display_name=? AND is_pending=0", new String[]{DIRECTORY, name}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return new Saved(ContentUris.withAppendedId(collection, cursor.getLong(0)), name, true);
            }
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, DIRECTORY);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) throw new IOException("无法创建相册图片，请检查存储空间");
        try {
            try (OutputStream output = resolver.openOutputStream(uri); InputStream input = new FileInputStream(file)) {
                if (output == null) throw new IOException("无法写入相册图片");
                input.transferTo(output);
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException("无法发布相册图片");
            return new Saved(uri, name, false);
        } catch (IOException | RuntimeException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
    }

    public List<Saved> list() {
        List<Saved> saved = new ArrayList<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        try (Cursor cursor = context.getContentResolver().query(collection,
                new String[]{"_id", "_display_name"}, "relative_path=? AND is_pending=0",
                new String[]{DIRECTORY}, "_display_name DESC")) {
            if (cursor != null) while (cursor.moveToNext()) {
                saved.add(new Saved(ContentUris.withAppendedId(collection, cursor.getLong(0)), cursor.getString(1), true));
            }
        }
        return saved;
    }
}
