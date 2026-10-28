package io.github.cw5n1h2txyewy.bingwallpapers

import android.content.Context

/** Bing size requests; downloaded dimensions are checked before publishing. */
enum class Resolution(
    private val labelResource: Int,
    @JvmField val suffix: String,
    @JvmField val width: Int,
    @JvmField val height: Int,
) {
    UHD(R.string.size_uhd, "UHD", 0, 0),
    FHD(R.string.size_fhd, "1920x1080", 1920, 1080),
    WUXGA(R.string.size_wuxga, "1920x1200", 1920, 1200),
    PORTRAIT(R.string.size_portrait, "1080x1920", 1080, 1920);

    /** Return the resolution label in the active interface language. */
    fun label(context: Context): String = context.getString(labelResource)

    /** Reject responses whose actual dimensions do not meet the requested size. */
    fun accepts(actualWidth: Int, actualHeight: Int): Boolean =
        if (this == UHD) actualWidth > 1920 && actualHeight >= 1080
        else actualWidth == width && actualHeight == height

    /** Preserve legacy full-HD names and suffix other sizes with their actual dimensions. */
    fun filename(date: String, actualWidth: Int, actualHeight: Int): String {
        require(Regex("[0-9]{8}").matches(date)) { "Invalid photo date: $date" }
        require(accepts(actualWidth, actualHeight)) { "Bing size mismatch: $actualWidth×$actualHeight" }
        val suffix = if (actualWidth == 1920 && actualHeight == 1080) "" else "-${actualWidth}x$actualHeight"
        return "$date$suffix.jpg"
    }
}
