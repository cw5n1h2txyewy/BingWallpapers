package io.github.cw5n1h2txyewy.bingwallpapers

import org.json.JSONException
import org.json.JSONObject

/** Immutable metadata from Bing, with a strict image-host boundary. */
class Wallpaper @Throws(JSONException::class) constructor(json: JSONObject) {
    // Bing CN starts at 16:00 UTC on the preceding day; enddate matches its displayed day.
    @JvmField val date: String = json.optString("enddate", json.getString("startdate"))
    @JvmField val title: String = json.optString("title", "Bing")
    @JvmField val copyright: String = json.getString("copyright")
    @JvmField val base: String = json.getString("urlbase")

    init {
        if (!Regex("[0-9]{8}").matches(date) || !Regex("/th\\?id=OHR\\.[A-Za-z0-9_.-]+").matches(base)) {
            throw JSONException("Invalid Bing image date or URL")
        }
    }

    /** Build a request on the fixed Bing image host. */
    fun url(size: String): String = "https://www.bing.com${base}_$size.jpg"

    /** Persist the displayed day so cached metadata survives Activity recreation. */
    @Throws(JSONException::class)
    fun toJson(): JSONObject = JSONObject().put("startdate", date).put("title", title)
        .put("copyright", copyright).put("urlbase", base)
}
