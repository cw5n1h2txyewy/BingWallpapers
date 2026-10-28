package io.github.bingwallpapers;

import org.junit.Test;
import static org.junit.Assert.*;

/** Keep directory filtering compatible with legacy photo names. */
public class WallpaperFilesTest {
    @Test public void acceptsLegacyAndHighResolutionNames() {
        for (String name : new String[]{"20180101.jpg", "20180101-1920x1200.jpg", "20261028-3840x2160.jpg", "20261028-1080x1920.jpg", "20240229.JPEG"})
            assertTrue(name, WallpaperFiles.matches(name));
    }
    @Test public void excludesUnrelatedAndUnsafeNames() {
        for (String name : new String[]{".nomedia", "cover.jpg", "20180101.jpg.tmp", "../20180101.jpg", "20180101.png", "20180101-0x1080.jpg", "20180101-1920x1200.jpg.exe", "20180101 (1).jpg", ""})
            assertFalse(name, WallpaperFiles.matches(name));
        assertFalse(WallpaperFiles.matches(null));
    }
    @Test public void rejectsInvalidCalendarDates() {
        assertFalse(WallpaperFiles.matches("20260229.jpg"));
        assertFalse(WallpaperFiles.matches("20261301.jpg"));
        assertFalse(WallpaperFiles.matches("20261032.jpg"));
    }
    @Test public void acceptsOnlyDownloadDirectory() {
        assertTrue(WallpaperFiles.isDownloadDirectory("primary:DCIM/BingWallpapers"));
        assertTrue(WallpaperFiles.isDownloadDirectory("ABCD-1234:DCIM/BingWallpapers"));
        assertFalse(WallpaperFiles.isDownloadDirectory("primary:DCIM"));
        assertFalse(WallpaperFiles.isDownloadDirectory("primary:DCIM/BingWallpapers/subfolder"));
        assertFalse(WallpaperFiles.isDownloadDirectory("primary:Pictures/BingWallpapers"));
        assertFalse(WallpaperFiles.isDownloadDirectory("DCIM/BingWallpapers"));
    }
}
