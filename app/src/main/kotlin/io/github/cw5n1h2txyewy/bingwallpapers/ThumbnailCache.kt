package io.github.cw5n1h2txyewy.bingwallpapers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

/** Bounded memory/disk preview cache with one decode or network request per image. */
class ThumbnailCache(private val directory: File, private val diskLimit: Long) {
    /** Supplies an uncached preview without modifying the downloaded original. */
    fun interface Loader {
        @Throws(IOException::class)
        fun load(): Bitmap?
    }

    private val memory = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = maxOf(1, bitmap.allocationByteCount / 1024)
    }
    private val pending = ConcurrentHashMap<String, CompletableFuture<Bitmap>>()

    /** Return an already decoded preview immediately on the UI thread. */
    fun peek(key: String): Bitmap? = memory.get(key)

    /** Prefer memory, then disk; coalesce concurrent misses and cache successful previews. */
    @Throws(IOException::class)
    fun get(key: String, loader: Loader): Bitmap {
        memory.get(key)?.let { return it }
        val owner = CompletableFuture<Bitmap>()
        val existing = pending.putIfAbsent(key, owner)
        if (existing != null) {
            return try {
                existing.get()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("预览加载已取消")
            } catch (error: ExecutionException) {
                throw IOException("预览加载失败", error.cause)
            }
        }
        try {
            val file = File(directory, hash(key) + ".jpg")
            val cached = BitmapFactory.decodeFile(file.absolutePath)
            val bitmap = cached ?: loader.load() ?: throw IOException("无法解码图片")
            if (cached == null) {
                // Cache storage failure must not make a successfully loaded photo disappear.
                try {
                    write(file, bitmap)
                } catch (error: IOException) {
                    Log.w("ThumbnailCache", "预览磁盘缓存不可用", error)
                }
            } else file.setLastModified(System.currentTimeMillis())
            memory.put(key, bitmap)
            owner.complete(bitmap)
            return bitmap
        } catch (error: Exception) {
            if (error !is IOException && error !is RuntimeException) throw error
            owner.completeExceptionally(error)
            throw error
        } finally {
            pending.remove(key, owner)
        }
    }

    /** Clear previews on an explicit local-library refresh. Call on a worker thread. */
    fun clear() {
        memory.evictAll()
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    /** Write one preview atomically and discard the oldest files above the byte budget. */
    @Throws(IOException::class)
    private fun write(file: File, bitmap: Bitmap) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建预览缓存目录")
        val temporary = File.createTempFile("preview-", ".tmp", directory)
        try {
            temporary.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw IOException("无法编码预览缓存")
            }
            if (!temporary.renameTo(file)) throw IOException("无法发布预览缓存")
        } finally {
            temporary.delete()
        }
        val files = directory.listFiles { _, name -> name.endsWith(".jpg") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (cached in files) {
            if (total <= diskLimit) break
            val size = cached.length()
            if (cached.delete()) total -= size
        }
    }

    /** Map arbitrary URLs and document URIs to safe fixed-length cache filenames. */
    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
}
