package io.github.cw5n1h2txyewy.bingwallpapers

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONException
import org.json.JSONObject

/** HTTPS-only Bing access with bounded downloads and explicit HTTP errors. */
object BingClient {
    /** Copy a bounded response to the caller's stream, supporting worker cancellation. */
    @JvmStatic
    @Throws(IOException::class)
    fun fetch(context: Context, url: String, target: OutputStream, limit: Int) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "BingWallpapers/1.0 (Android)")
        try {
            val code = connection.responseCode
            if (code != 200) throw IOException(context.getString(R.string.http_failed, code))
            connection.inputStream.use { input ->
                val buffer = ByteArray(32768)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedIOException(context.getString(R.string.request_cancelled))
                    }
                    total += count
                    if (total > limit) throw IOException(context.getString(R.string.response_large))
                    target.write(buffer, 0, count)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Fetch the eight daily entries for the selected Bing market. */
    @JvmStatic
    @Throws(IOException::class)
    fun metadata(context: Context, market: String): String {
        val output = ByteArrayOutputStream()
        fetch(context, "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=$market", output, 1024 * 1024)
        return output.toString(Charsets.UTF_8.name())
    }

    /** Validate a nonempty metadata response before replacing the cached list. */
    @JvmStatic
    @Throws(JSONException::class)
    fun parse(raw: String): List<Wallpaper> {
        val array = JSONObject(raw).getJSONArray("images")
        val images = List(array.length()) { Wallpaper(array.getJSONObject(it)) }
        if (images.isEmpty()) throw JSONException("Bing returned no images")
        return images
    }
}
