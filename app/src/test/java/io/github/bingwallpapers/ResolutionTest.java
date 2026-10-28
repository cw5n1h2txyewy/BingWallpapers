package io.github.bingwallpapers;

import org.junit.Test;
import static org.junit.Assert.*;

/** Protect the user's existing date-based photo naming contract. */
public class ResolutionTest {
    @Test public void fullHdKeepsLegacyName() { assertEquals("20180101.jpg", Resolution.FHD.filename("20180101", 1920, 1080)); }
    @Test public void widescreenAddsDimensions() { assertEquals("20180101-1920x1200.jpg", Resolution.WUXGA.filename("20180101", 1920, 1200)); }
    @Test public void uhdUsesActualSize() {
        assertEquals("20180101-3840x2160.jpg", Resolution.UHD.filename("20180101", 3840, 2160));
        assertEquals("20180101-5349x3009.jpg", Resolution.UHD.filename("20180101", 5349, 3009));
    }
    @Test public void portraitAddsDimensions() { assertEquals("20180101-1080x1920.jpg", Resolution.PORTRAIT.filename("20180101", 1080, 1920)); }
    @Test public void lowResolutionCannotMasqueradeAsUhd() { assertFalse(Resolution.UHD.accepts(1920, 1080)); assertFalse(Resolution.UHD.accepts(-1, -1)); }
    @Test(expected = IllegalArgumentException.class) public void invalidDateRejected() { Resolution.FHD.filename("../../file", 1920, 1080); }
    @Test(expected = IllegalArgumentException.class) public void wrongSizeRejected() { Resolution.WUXGA.filename("20180101", 1920, 1080); }
}
