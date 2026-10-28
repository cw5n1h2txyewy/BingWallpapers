package io.github.cw5n1h2txyewy.bingwallpapers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Recognize the date-based names shared by the old and new wallpaper apps. */
public final class WallpaperFiles {
    private static final Pattern NAME = Pattern.compile("[0-9]{8}(?:-[1-9][0-9]{0,4}x[1-9][0-9]{0,4})?\\.jpe?g", Pattern.CASE_INSENSITIVE);
    private WallpaperFiles() {}

    /** Accept only valid dates with an optional resolution suffix and JPEG extension. */
    public static boolean matches(String name) {
        if (name == null || !NAME.matcher(name).matches()) return false;
        try {
            LocalDate.parse(name.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            return true;
        } catch (DateTimeParseException error) { return false; }
    }

    /** Limit directory grants to the requested download folder, on any storage volume. */
    public static boolean isDownloadDirectory(String documentId) {
        int colon = documentId.indexOf(':');
        return colon > 0 && documentId.substring(colon + 1).equalsIgnoreCase("DCIM/BingWallpapers");
    }
}
