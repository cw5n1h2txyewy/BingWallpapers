package io.github.cw5n1h2txyewy.bingwallpapers;

import android.content.*;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

/** Publishes validated JPEG originals atomically to the system photo library. */
public final class ImageStore {
    public static final String DIRECTORY = "DCIM/BingWallpapers/";
    private final Context context;
    public ImageStore(Context context) { this.context = AppSettings.wrap(context.getApplicationContext()); }

    public record Saved(Uri uri, String name, boolean existing) {}

    public Saved save(Wallpaper image, Resolution resolution) throws IOException {
        File temporary = File.createTempFile("bing-", ".jpg", context.getCacheDir());
        try {
            try (OutputStream output = new FileOutputStream(temporary)) {
                BingClient.fetch(context, image.url(resolution.suffix), output, 40 * 1024 * 1024);
            }
            return publish(temporary, image.date, resolution);
        } finally { if (!temporary.delete()) temporary.deleteOnExit(); }
    }

    /** Validate and publish a completed JPEG, without modifying an existing photo. */
    public Saved publish(File file, String date, Resolution resolution) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (!"image/jpeg".equals(bounds.outMimeType) || !resolution.accepts(bounds.outWidth, bounds.outHeight)) {
            throw new IOException(context.getString(R.string.size_unavailable, resolution.label(context), bounds.outWidth, bounds.outHeight));
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (input.length() < 4) throw new IOException(context.getString(R.string.image_empty));
            input.seek(input.length() - 2);
            if (input.readUnsignedShort() != 0xffd9) throw new IOException(context.getString(R.string.jpeg_incomplete));
        }
        String name = resolution.filename(date, bounds.outWidth, bounds.outHeight);
        if (directoryUri() != null) {
            for (Saved saved : listDirectory()) if (saved.name().equalsIgnoreCase(name)) return saved;
        }
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
        if (uri == null) throw new IOException(context.getString(R.string.create_failed));
        try {
            try (OutputStream output = resolver.openOutputStream(uri); InputStream input = new FileInputStream(file)) {
                if (output == null) throw new IOException(context.getString(R.string.write_failed));
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException(context.getString(R.string.publish_failed));
            SharedPreferences preferences = context.getSharedPreferences("bing", Context.MODE_PRIVATE);
            preferences.edit().putLong("library-revision", preferences.getLong("library-revision", 0) + 1).apply();
            return new Saved(uri, name, false);
        } catch (IOException | RuntimeException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
    }

    public List<Saved> list() {
        if (directoryUri() != null) return listDirectory();
        List<Saved> saved = new ArrayList<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        try (Cursor cursor = context.getContentResolver().query(collection,
                new String[]{"_id", "_display_name"}, "relative_path=? AND is_pending=0",
                new String[]{DIRECTORY}, "_display_name DESC")) {
            if (cursor != null) while (cursor.moveToNext()) {
                if (WallpaperFiles.matches(cursor.getString(1)))
                    saved.add(new Saved(ContentUris.withAppendedId(collection, cursor.getLong(0)), cursor.getString(1), true));
            }
        }
        return saved;
    }

    /** Return the explicitly chosen directory only while its persistent read grant exists. */
    public Uri directoryUri() {
        String raw = context.getSharedPreferences("bing", Context.MODE_PRIVATE).getString("directory", null);
        if (raw == null) return null;
        Uri uri = Uri.parse(raw);
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions())
            if (permission.getUri().equals(uri) && permission.isReadPermission()) return uri;
        return null;
    }

    /** Persist a read-only grant for the exact wallpaper directory selected by the user. */
    public void selectDirectory(Uri uri) {
        if (!"com.android.externalstorage.documents".equals(uri.getAuthority())
                || !WallpaperFiles.isDownloadDirectory(DocumentsContract.getTreeDocumentId(uri)))
            throw new IllegalArgumentException(context.getString(R.string.wrong_directory));
        context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.getSharedPreferences("bing", Context.MODE_PRIVATE).edit().putString("directory", uri.toString()).apply();
    }

    /** Enumerate direct child documents without consulting the MediaStore index. */
    private List<Saved> listDirectory() {
        Uri tree = directoryUri();
        if (tree == null) throw new IllegalStateException(context.getString(R.string.directory_revoked));
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        List<Saved> saved = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null) throw new IllegalStateException(context.getString(R.string.directory_unreadable));
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2)) && WallpaperFiles.matches(name))
                    saved.add(new Saved(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)), name, true));
            }
        }
        saved.sort(Comparator.comparing(Saved::name, String.CASE_INSENSITIVE_ORDER).reversed());
        return saved;
    }

    /** Decode a bounded preview directly from a document or media URI. */
    public Bitmap thumbnail(Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException(context.getString(R.string.image_unreadable));
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException(context.getString(R.string.image_invalid));
        options.inSampleSize = 1;
        while (options.outWidth / (options.inSampleSize * 2) >= 960 || options.outHeight / (options.inSampleSize * 2) >= 960)
            options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException(context.getString(R.string.image_unreadable));
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException(context.getString(R.string.image_decode_failed));
            return bitmap;
        }
    }
}
