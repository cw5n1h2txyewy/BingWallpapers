package io.github.bingwallpapers;

/** Bing size requests; downloaded dimensions are checked before publishing. */
public enum Resolution {
    UHD("UHD 原图 · 通常为 4K", "UHD", 0, 0),
    FHD("1920 × 1080 · 全高清", "1920x1080", 1920, 1080),
    WUXGA("1920 × 1200", "1920x1200", 1920, 1200),
    PORTRAIT("1080 × 1920 · 竖屏", "1080x1920", 1080, 1920);

    public final String label;
    public final String suffix;
    public final int width;
    public final int height;

    Resolution(String label, String suffix, int width, int height) {
        this.label = label; this.suffix = suffix; this.width = width; this.height = height;
    }

    public boolean accepts(int actualWidth, int actualHeight) {
        return this == UHD ? actualWidth > 1920 && actualHeight >= 1080
                : actualWidth == width && actualHeight == height;
    }

    public String filename(String date, int actualWidth, int actualHeight) {
        if (!date.matches("[0-9]{8}")) throw new IllegalArgumentException("无效的图片日期：" + date);
        if (!accepts(actualWidth, actualHeight)) throw new IllegalArgumentException("Bing 未提供请求的分辨率，实际为 " + actualWidth + "×" + actualHeight);
        return date + (actualWidth == 1920 && actualHeight == 1080 ? "" : "-" + actualWidth + "x" + actualHeight) + ".jpg";
    }
}
