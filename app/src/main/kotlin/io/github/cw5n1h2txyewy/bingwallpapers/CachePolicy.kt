package io.github.cw5n1h2txyewy.bingwallpapers

import java.time.Instant
import java.time.ZoneId

/** Freshness rules independent of Android UI and wall-clock sampling. */
object CachePolicy {
    /** Reuse daily metadata only within the same local day and never from the future. */
    @JvmStatic
    fun sameDay(fetchedAt: Long, now: Long, zone: ZoneId): Boolean =
        fetchedAt > 0 && fetchedAt <= now &&
            Instant.ofEpochMilli(fetchedAt).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    /** Throttle failed automatic refreshes while allowing retry after a clock reversal. */
    @JvmStatic
    fun retryAllowed(previousAttempt: Long, now: Long): Boolean =
        previousAttempt <= 0 || now < previousAttempt || now - previousAttempt >= 60_000
}
