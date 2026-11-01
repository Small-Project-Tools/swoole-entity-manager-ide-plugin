package dev.smallproject.ide

import java.io.File
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeIndexSnapshotCacheTest {
    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        val size: Int get() = tasks.size
        fun runNext() { tasks.removeFirst().run() }
    }

    private val root = File("/example/local-project")

    private fun model(field: String) = NativeProjectIndex(
        entities = mapOf("Demo\\Person" to NativeEntity(setOf(field), emptyMap())),
        managers = mapOf("Demo\\PersonManager" to "Demo\\Person"),
    )

    private val context = QueryContext(kind = "field", manager = "Demo\\PersonManager", rootAlias = "person")

    @Test fun firstCompletionReturnsActualFieldsImmediately() {
        val executor = ManualExecutor()
        var scans = 0
        val cache = NativeIndexSnapshotCache(executor, indexer = { _, _ ->
            scans++
            model("email")
        })
        assertFalse(cache.isIndexed(root))
        assertEquals(listOf("email"), cache.get(root).complete(context))
        assertEquals(1, scans)
        assertTrue(cache.isIndexed(root))
        assertEquals(0, executor.size)
    }

    @Test fun repeatedLookupsNeverQueueOrRepeatAnInitialScan() {
        val executor = ManualExecutor()
        var scans = 0
        val cache = NativeIndexSnapshotCache(executor, indexer = { _, _ ->
            scans++
            model("id")
        })
        repeat(10) { assertEquals(listOf("id"), cache.get(root).complete(context)) }
        assertEquals(1, scans)
        assertEquals(0, executor.size)
    }

    @Test fun prewarmingComputesOnceAndExposesTheSameSnapshot() {
        val executor = ManualExecutor()
        var scans = 0
        val cache = NativeIndexSnapshotCache(executor, indexer = { _, _ ->
            scans++
            model("createdAt")
        })
        cache.warmUp(root)
        assertEquals(1, executor.size)
        executor.runNext()
        assertEquals(1, scans)
        assertEquals(listOf("createdAt"), cache.get(root).complete(context))
        assertEquals(1, scans)
    }

    @Test fun staleSnapshotIsReturnedWhileRefreshingOffThread() {
        val executor = ManualExecutor()
        var now = 1_000L
        var scans = 0
        val cache = NativeIndexSnapshotCache(executor,
            clock = { now }, ttlMillis = 30L,
            indexer = { _, _ -> scans++; model(if (scans == 1) "oldField" else "newField") },
        )
        assertEquals(listOf("oldField"), cache.get(root).complete(context))
        now += 31
        assertEquals(listOf("oldField"), cache.get(root).complete(context))
        assertEquals(1, executor.size)
        assertEquals(1, scans)
        executor.runNext()
        assertEquals(listOf("newField"), cache.get(root).complete(context))
        assertEquals(2, scans)
    }

    @Test fun failedRefreshKeepsLastWorkingSnapshot() {
        val executor = ManualExecutor()
        var now = 1_000L
        var scans = 0
        val cache = NativeIndexSnapshotCache(executor, clock = { now }, ttlMillis = 30L,
            indexer = { _, _ ->
                scans++
                if (scans > 1) error("unavailable")
                model("name")
            },
        )
        assertEquals(listOf("name"), cache.get(root).complete(context))
        now += 40
        assertEquals(listOf("name"), cache.get(root).complete(context))
        executor.runNext()
        assertEquals(listOf("name"), cache.get(root).complete(context))
    }

    @Test fun nativeScannerHonorsCancellationWithoutLaunchingPhp() {
        val temp = kotlin.io.path.createTempDirectory("small-orm-cancel").toFile()
        try {
            assertFailsWith<IllegalArgumentException> {
                NativeProjectScanner.scan(temp) { throw IllegalArgumentException("interrupted") }
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test fun cancellationDoesNotLeaveAFalseEmptySnapshot() {
        val executor = ManualExecutor()
        var attempts = 0
        val cache = NativeIndexSnapshotCache(executor, indexer = { _, _ ->
            attempts++
            model("id")
        })
        assertFailsWith<IllegalArgumentException> {
            cache.get(root) { throw IllegalArgumentException("cancelled") }
        }
        assertFalse(cache.isIndexed(root))
        assertEquals(listOf("id"), cache.get(root).complete(context))
        assertEquals(1, attempts)
    }
}
