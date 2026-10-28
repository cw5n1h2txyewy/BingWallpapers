package io.github.cw5n1h2txyewy.bingwallpapers

/** Small keyed snapshot cache that rejects expired or invalidated data. */
class SnapshotCache<T>(private val lifetimeMillis: Long) {
    private var key: String? = null
    private var loadedAt: Long = 0
    private var value: T? = null

    /** Return the value only for the same source revision inside its reuse window. */
    fun get(requestedKey: String, now: Long): T? =
        value.takeIf { requestedKey == key && now >= loadedAt && now - loadedAt < lifetimeMillis }

    /** Replace the cached snapshot after a successful load. */
    fun put(sourceKey: String, snapshot: T, now: Long) {
        key = sourceKey
        value = snapshot
        loadedAt = now
    }

    /** Explicitly invalidate data after refresh, permission changes or downloads. */
    fun clear() {
        value = null
        key = null
    }
}
