package io.github.cw5n1h2txyewy.bingwallpapers;

/** Bing size requests; downloaded dimensions are checked before publishing. */
public enum Resolution {
    UHD(R.string.size_uhd, "UHD", 0, 0),
    FHD(R.string.size_fhd, "1920x1080", 1920, 1080),
    WUXGA(R.string.size_wuxga, "1920x1200", 1920, 1200),
    PORTRAIT(R.string.size_portrait, "1080x1920", 1080, 1920);

    private final int labelResource;
    public final String suffix;
    public final int width;
    public final int height;

    Resolution(int labelResource, String suffix, int width, int height) {
        this.labelResource = labelResource; this.suffix = suffix; this.width = width; this.height = height;
    }

    /** Return the resolution label in the active interface language. */
    public String label(android.content.Context context) { return context.getString(labelResource); }

    public boolean accepts(int actualWidth, int actualHeight) {
        return this == UHD ? actualWidth > 1920 && actualHeight >= 1080
                : actualWidth == width && actualHeight == height;
    }

    public String filename(String date, int actualWidth, int actualHeight) {
        if (!date.matches("[0-9]{8}")) throw new IllegalArgumentException("Invalid photo date: " + date);
        if (!accepts(actualWidth, actualHeight)) throw new IllegalArgumentException("Bing size mismatch: " + actualWidth + "×" + actualHeight);
        return date + (actualWidth == 1920 && actualHeight == 1080 ? "" : "-" + actualWidth + "x" + actualHeight) + ".jpg";
    }
}
