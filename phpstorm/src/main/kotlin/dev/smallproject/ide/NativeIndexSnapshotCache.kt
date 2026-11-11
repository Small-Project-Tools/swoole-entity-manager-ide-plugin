package dev.smallproject.ide

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Native metadata remains available synchronously during IDE completion.
 *
 * The first lookup is cancellable and computed on IntelliJ's background completion
 * thread. Subsequent lookups are instantaneous in-memory reads. Refreshes never
 * replace a valid snapshot with an empty one while indexing is in flight.
 */
internal class NativeIndexSnapshotCache(
    private val executor: Executor,
    private val indexer: (File, () -> Unit) -> NativeProjectIndex = NativeProjectScanner::scan,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 30_000L,
) {
    private data class Snapshot(val indexedAt: Long, val model: NativeProjectIndex)

    private val snapshots = ConcurrentHashMap<String, Snapshot>()
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    fun get(root: File, checkCanceled: () -> Unit = {}): NativeProjectIndex {
        val key = root.absolutePath
        val cached = snapshots[key]
        if (cached == null) {
            checkCanceled()
            return snapshots.computeIfAbsent(key) {
                val model = indexer(root, checkCanceled)
                Snapshot(clock(), model)
            }.model
        }
        if (clock() - cached.indexedAt >= ttlMillis) refresh(root, cached)
        return cached.model
    }

    fun warmUp(root: File) {
        if (snapshots.containsKey(root.absolutePath)) return
        submit {
            // The first editor request can also enter here via computeIfAbsent.
            // A concurrent scan is automatically coalesced.
            get(root)
        }
    }

    fun isIndexed(root: File): Boolean = snapshots.containsKey(root.absolutePath)

    private fun refresh(root: File, expired: Snapshot) {
        val key = root.absolutePath
        // A caller may have captured the expired snapshot just before another
        // thread finished refreshing it. Never schedule another scan for it.
        if (snapshots[key] !== expired || !refreshing.add(key)) return
        if (snapshots[key] !== expired) {
            refreshing.remove(key)
            return
        }
        if (!submit {
                try {
                    val model = indexer(root) {}
                    snapshots[key] = Snapshot(clock(), model)
                } finally {
                    refreshing.remove(key)
                }
            }) {
            refreshing.remove(key)
        }
    }

    private fun submit(task: () -> Unit): Boolean =
        try {
            executor.execute { runCatching(task) }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
}
