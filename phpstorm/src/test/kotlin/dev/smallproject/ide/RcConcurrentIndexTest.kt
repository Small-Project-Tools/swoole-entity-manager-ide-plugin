package dev.smallproject.ide

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcConcurrentIndexTest {
    private val root = File("/virtual/rc-project")
    private val query = QueryContext("field", "Rc\\EntityManager\\UserManager", "u")

    private fun model(name: String) = NativeProjectIndex(
        entities = mapOf("Rc\\Entity\\User" to NativeEntity(setOf(name), emptyMap())),
        managers = mapOf("Rc\\EntityManager\\UserManager" to "Rc\\Entity\\User"),
    )

    @Test fun manyParallelFirstCompletionsShareOneIndexSnapshot() {
        val pool = Executors.newFixedThreadPool(12)
        val barrier = CountDownLatch(1)
        val scans = AtomicInteger()
        val cache = NativeIndexSnapshotCache(pool, indexer = { _, _ ->
            scans.incrementAndGet()
            Thread.sleep(25)
            model("id")
        })
        try {
            val futures = (0 until 80).map {
                pool.submit<List<String>> {
                    barrier.await()
                    cache.get(root).complete(query)
                }
            }
            barrier.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(results.all { it == listOf("id") })
            assertEquals(1, scans.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun manyParallelExpiredReadsNeverReturnAnEmptyIndex() {
        val workers = Executors.newFixedThreadPool(12)
        val now = AtomicLong(1000)
        val scans = AtomicInteger()
        val cache = NativeIndexSnapshotCache(workers, clock = now::get, ttlMillis = 30,
            indexer = { _, _ ->
                val number = scans.incrementAndGet()
                if (number > 1) Thread.sleep(35)
                model(if (number == 1) "before" else "after")
            },
        )
        try {
            assertEquals(listOf("before"), cache.get(root).complete(query))
            now.addAndGet(31)
            val futures = (0 until 80).map {
                workers.submit<List<String>> { cache.get(root).complete(query) }
            }
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(results.all { it == listOf("before") || it == listOf("after") })
            assertTrue(scans.get() <= 2)
        } finally {
            workers.shutdownNow()
        }
    }
}
