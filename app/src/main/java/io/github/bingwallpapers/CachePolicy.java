package io.github.bingwallpapers;

import java.time.Instant;
import java.time.ZoneId;

/** Freshness rules independent of Android UI and wall-clock sampling. */
public final class CachePolicy {
    private CachePolicy() {}

    /** Reuse daily metadata only within the same local day and never from the future. */
    public static boolean sameDay(long fetchedAt, long now, ZoneId zone) {
        return fetchedAt > 0 && fetchedAt <= now
                && Instant.ofEpochMilli(fetchedAt).atZone(zone).toLocalDate()
                .equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate());
    }

    /** Throttle failed automatic refreshes while allowing retry after a clock reversal. */
    public static boolean retryAllowed(long previousAttempt, long now) {
        return previousAttempt <= 0 || now < previousAttempt || now - previousAttempt >= 60_000;
    }
}
