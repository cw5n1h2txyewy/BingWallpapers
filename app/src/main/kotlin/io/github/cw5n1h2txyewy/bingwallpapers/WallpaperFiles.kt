package io.github.cw5n1h2txyewy.bingwallpapers

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Recognize the date-based names shared by the old and new wallpaper apps. */
object WallpaperFiles {
    private val namePattern = Regex("[0-9]{8}(?:-[1-9][0-9]{0,4}x[1-9][0-9]{0,4})?\\.jpe?g", RegexOption.IGNORE_CASE)

    /** Accept only valid dates with an optional resolution suffix and JPEG extension. */
    @JvmStatic
    fun matches(name: String?): Boolean {
        if (name == null || !namePattern.matches(name)) return false
        return try {
            LocalDate.parse(name.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    /** Limit directory grants to the requested download folder, on any storage volume. */
    @JvmStatic
    fun isDownloadDirectory(documentId: String): Boolean {
        val colon = documentId.indexOf(':')
        return colon > 0 && documentId.substring(colon + 1).equals("DCIM/BingWallpapers", ignoreCase = true)
    }
}
