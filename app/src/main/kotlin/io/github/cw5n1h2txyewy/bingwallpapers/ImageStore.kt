package io.github.cw5n1h2txyewy.bingwallpapers

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Publishes validated JPEG originals atomically to the system photo library. */
class ImageStore(context: Context) {
    private val context = AppSettings.wrap(context.applicationContext)

    /** A saved photo and whether an existing file was reused. */
    data class Saved(val uri: Uri, val name: String, val existing: Boolean) {
        // Keep the original record accessors for existing Java integration checks.
        fun uri(): Uri = uri
        fun name(): String = name
        fun existing(): Boolean = existing
    }

    /** Download an original to private temporary storage before publishing it. */
    @Throws(IOException::class)
    fun save(image: Wallpaper, resolution: Resolution): Saved {
        val temporary = File.createTempFile("bing-", ".jpg", context.cacheDir)
        try {
            temporary.outputStream().use {
                BingClient.fetch(context, image.url(resolution.suffix), it, 40 * 1024 * 1024)
            }
            return publish(temporary, image.date, resolution)
        } finally {
            if (!temporary.delete()) temporary.deleteOnExit()
        }
    }

    /** Validate and publish a completed JPEG, without modifying an existing photo. */
    @Throws(IOException::class)
    fun publish(file: File, date: String, resolution: Resolution): Saved {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outMimeType != "image/jpeg" || !resolution.accepts(bounds.outWidth, bounds.outHeight)) {
            throw IOException(context.getString(R.string.size_unavailable, resolution.label(context), bounds.outWidth, bounds.outHeight))
        }
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 4) throw IOException(context.getString(R.string.image_empty))
            input.seek(input.length() - 2)
            if (input.readUnsignedShort() != 0xffd9) throw IOException(context.getString(R.string.jpeg_incomplete))
        }
        val name = resolution.filename(date, bounds.outWidth, bounds.outHeight)
        if (directoryUri() != null) {
            listDirectory().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
            "relative_path=? AND _display_name=? AND is_pending=0", arrayOf(DIRECTORY, name), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return Saved(ContentUris.withAppendedId(collection, cursor.getLong(0)), name, true)
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, DIRECTORY)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException(context.getString(R.string.create_failed))
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException(context.getString(R.string.write_failed))
            output.use { target -> file.inputStream().use { it.copyTo(target, 32768) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) != 1) throw IOException(context.getString(R.string.publish_failed))
            val preferences = context.getSharedPreferences("bing", Context.MODE_PRIVATE)
            preferences.edit().putLong("library-revision", preferences.getLong("library-revision", 0) + 1).apply()
            return Saved(uri, name, false)
        } catch (error: Exception) {
            if (error !is IOException && error !is RuntimeException) throw error
            resolver.delete(uri, null, null)
            throw error
        }
    }

    /** Enumerate the selected directory, falling back to media visible to this application. */
    fun list(): List<Saved> {
        if (directoryUri() != null) return listDirectory()
        val saved = mutableListOf<Saved>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(collection, arrayOf("_id", "_display_name"),
            "relative_path=? AND is_pending=0", arrayOf(DIRECTORY), "_display_name DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name != null && WallpaperFiles.matches(name)) {
                    saved.add(Saved(ContentUris.withAppendedId(collection, cursor.getLong(0)), name, true))
                }
            }
        }
        return saved
    }

    /** Return the explicitly chosen directory only while its persistent read grant exists. */
    fun directoryUri(): Uri? {
        val raw = context.getSharedPreferences("bing", Context.MODE_PRIVATE).getString("directory", null) ?: return null
        val uri = Uri.parse(raw)
        return uri.takeIf { context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission } }
    }

    /** Persist a read-only grant for the exact wallpaper directory selected by the user. */
    fun selectDirectory(uri: Uri) {
        require(uri.authority == "com.android.externalstorage.documents" &&
            WallpaperFiles.isDownloadDirectory(DocumentsContract.getTreeDocumentId(uri))) {
            context.getString(R.string.wrong_directory)
        }
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.getSharedPreferences("bing", Context.MODE_PRIVATE).edit().putString("directory", uri.toString()).apply()
    }

    /** Enumerate direct child documents without consulting the MediaStore index. */
    private fun listDirectory(): List<Saved> {
        val tree = directoryUri() ?: throw IllegalStateException(context.getString(R.string.directory_revoked))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val saved = mutableListOf<Saved>()
        val cursor = context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null
        ) ?: throw IllegalStateException(context.getString(R.string.directory_unreadable))
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(1)
                if (name != null && it.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR && WallpaperFiles.matches(name)) {
                    saved.add(Saved(DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)), name, true))
                }
            }
        }
        return saved.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).asReversed()
    }

    /** Decode a bounded preview directly from a document or media URI. */
    @Throws(IOException::class)
    fun thumbnail(uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val resolver = context.contentResolver
        (resolver.openInputStream(uri) ?: throw IOException(context.getString(R.string.image_unreadable))).use {
            BitmapFactory.decodeStream(it, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException(context.getString(R.string.image_invalid))
        options.inSampleSize = 1
        while (options.outWidth / (options.inSampleSize * 2) >= 960 || options.outHeight / (options.inSampleSize * 2) >= 960) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        (resolver.openInputStream(uri) ?: throw IOException(context.getString(R.string.image_unreadable))).use {
            return BitmapFactory.decodeStream(it, null, options) ?: throw IOException(context.getString(R.string.image_decode_failed))
        }
    }

    companion object {
        const val DIRECTORY = "DCIM/BingWallpapers/"
    }
}
