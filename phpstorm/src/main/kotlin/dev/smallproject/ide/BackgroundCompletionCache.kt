package dev.smallproject.ide

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Single-flight, non-blocking completion cache.
 * No calls to PHP, the filesystem indexer or JetBrains APIs occur on the caller's thread.
 */
internal class BackgroundCompletionCache(
    private val executor: Executor,
    private val ttlNanos: Long = TimeUnit.MINUTES.toNanos(2),
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private data class Entry(val expiresAt: Long, val labels: List<String>)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    fun complete(key: String, load: () -> List<String>, onReady: () -> Unit): List<String> {
        cache[key]?.takeIf { nanoTime() - it.expiresAt < 0 }?.let { return it.labels }
        if (!pending.add(key)) return emptyList()
        try {
            executor.execute {
                try {
                    val labels = runCatching(load).getOrDefault(emptyList())
                    // Negative results are short-lived, so transient indexing failures are retried.
                    val lifetime = if (labels.isEmpty()) TimeUnit.SECONDS.toNanos(10) else ttlNanos
                    if (cache.size >= 256) cache.clear()
                    cache[key] = Entry(nanoTime() + lifetime, labels)
                    if (labels.isNotEmpty()) runCatching(onReady)
                } finally {
                    pending.remove(key)
                }
            }
        } catch (_: RejectedExecutionException) {
            pending.remove(key)
        }
        return emptyList()
    }
}
