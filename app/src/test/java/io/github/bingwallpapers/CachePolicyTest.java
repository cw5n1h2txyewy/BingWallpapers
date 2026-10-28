package io.github.bingwallpapers;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;
import static org.junit.Assert.*;

/** Daily refresh boundaries and source-aware directory snapshot expiration. */
public class CachePolicyTest {
    private static long time(String iso) { return Instant.parse(iso).toEpochMilli(); }

    @Test public void reusesMetadataDuringSameLocalDay() {
        assertTrue(CachePolicy.sameDay(time("2026-10-28T00:00:00Z"), time("2026-10-28T15:59:59Z"), ZoneId.of("Asia/Shanghai")));
    }
    @Test public void refreshesAtLocalMidnight() {
        assertFalse(CachePolicy.sameDay(time("2026-10-28T15:59:59Z"), time("2026-10-28T16:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }
    @Test public void ignoresMissingAndFutureMetadataTimestamps() {
        assertFalse(CachePolicy.sameDay(0, time("2026-10-28T12:00:00Z"), ZoneId.of("UTC")));
        assertFalse(CachePolicy.sameDay(time("2026-10-28T13:00:00Z"), time("2026-10-28T12:00:00Z"), ZoneId.of("UTC")));
    }
    @Test public void directorySnapshotReusedUntilExpiration() {
        SnapshotCache<String> cache = new SnapshotCache<>(300_000);
        assertNull(cache.get("directory|1", 1000));
        cache.put("directory|1", "files", 1000);
        assertEquals("files", cache.get("directory|1", 300_999));
        assertNull(cache.get("directory|1", 301_000));
    }
    @Test public void permissionDownloadAndRefreshRevisionsInvalidateSnapshots() {
        SnapshotCache<String> cache = new SnapshotCache<>(300_000);
        cache.put("directory|1", "old", 1000);
        assertNull(cache.get("other-directory|1", 2000));
        assertNull(cache.get("directory|2", 2000));
        cache.clear(); assertNull(cache.get("directory|1", 2000));
    }
    @Test public void clockReversalDoesNotMakeSnapshotFreshForever() {
        SnapshotCache<String> cache = new SnapshotCache<>(300_000);
        cache.put("directory", "old", 2000);
        assertNull(cache.get("directory", 1000));
    }
    @Test public void automaticRetriesAreThrottledButSurviveClockChanges() {
        assertTrue(CachePolicy.retryAllowed(0, 1000));
        assertFalse(CachePolicy.retryAllowed(1000, 60_999));
        assertTrue(CachePolicy.retryAllowed(1000, 61_000));
        assertTrue(CachePolicy.retryAllowed(2000, 1000));
    }
}
