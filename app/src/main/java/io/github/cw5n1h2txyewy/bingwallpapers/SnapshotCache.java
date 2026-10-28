package io.github.cw5n1h2txyewy.bingwallpapers;

/** Small keyed snapshot cache that rejects expired or invalidated data. */
public final class SnapshotCache<T> {
    private final long lifetimeMillis;
    private String key;
    private long loadedAt;
    private T value;

    public SnapshotCache(long lifetimeMillis) { this.lifetimeMillis = lifetimeMillis; }

    /** Return the value only for the same source revision inside its reuse window. */
    public T get(String requestedKey, long now) {
        return value != null && requestedKey.equals(key) && now >= loadedAt && now - loadedAt < lifetimeMillis ? value : null;
    }

    /** Replace the cached snapshot after a successful load. */
    public void put(String sourceKey, T snapshot, long now) { key = sourceKey; value = snapshot; loadedAt = now; }

    /** Explicitly invalidate data after refresh, permission changes or downloads. */
    public void clear() { value = null; key = null; }
}
