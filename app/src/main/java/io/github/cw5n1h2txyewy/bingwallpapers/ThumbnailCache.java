package io.github.cw5n1h2txyewy.bingwallpapers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded memory/disk preview cache with one decode or network request per image. */
public final class ThumbnailCache {
    @FunctionalInterface public interface Loader {
        /** Load an uncached preview without modifying the downloaded original. */
        Bitmap load() throws IOException;
    }
    private final File directory;
    private final long diskLimit;
    private final LruCache<String, Bitmap> memory = new LruCache<>(24 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return Math.max(1, bitmap.getAllocationByteCount() / 1024); }
    };
    private final ConcurrentHashMap<String, CompletableFuture<Bitmap>> pending = new ConcurrentHashMap<>();

    public ThumbnailCache(File directory, long diskLimit) { this.directory = directory; this.diskLimit = diskLimit; }

    /** Return an already decoded preview immediately on the UI thread. */
    public Bitmap peek(String key) { return memory.get(key); }

    /** Prefer memory, then disk; coalesce concurrent misses and cache successful previews. */
    public Bitmap get(String key, Loader loader) throws IOException {
        Bitmap bitmap = memory.get(key);
        if (bitmap != null) return bitmap;
        CompletableFuture<Bitmap> owner = new CompletableFuture<>();
        CompletableFuture<Bitmap> existing = pending.putIfAbsent(key, owner);
        if (existing != null) {
            try { return existing.get(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new InterruptedIOException("预览加载已取消"); }
            catch (ExecutionException error) { throw new IOException("预览加载失败", error.getCause()); }
        }
        try {
            File file = new File(directory, hash(key) + ".jpg");
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) {
                bitmap = loader.load();
                if (bitmap == null) throw new IOException("无法解码图片");
                // Cache storage failure must not make a successfully loaded photo disappear.
                try { write(file, bitmap); } catch (IOException error) { android.util.Log.w("ThumbnailCache", "预览磁盘缓存不可用", error); }
            } else file.setLastModified(System.currentTimeMillis());
            memory.put(key, bitmap); owner.complete(bitmap); return bitmap;
        } catch (IOException | RuntimeException error) { owner.completeExceptionally(error); throw error; }
        finally { pending.remove(key, owner); }
    }

    /** Clear previews on an explicit local-library refresh. Call on a worker thread. */
    public void clear() {
        memory.evictAll();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) file.delete();
    }

    /** Write one preview atomically and discard the oldest files above the byte budget. */
    private void write(File file, Bitmap bitmap) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建预览缓存目录");
        File temporary = File.createTempFile("preview-", ".tmp", directory);
        try {
            try (OutputStream output = new FileOutputStream(temporary)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw new IOException("无法编码预览缓存");
            }
            if (!temporary.renameTo(file)) throw new IOException("无法发布预览缓存");
        } finally { temporary.delete(); }
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long total = 0;
        for (File cached : files) total += cached.length();
        for (File cached : files) {
            if (total <= diskLimit) break;
            long size = cached.length(); if (cached.delete()) total -= size;
        }
    }

    /** Map arbitrary URLs and document URIs to safe fixed-length cache filenames. */
    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte value : digest) name.append(String.format(Locale.ROOT, "%02x", value & 255));
            return name.toString();
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable", error); }
    }
}
