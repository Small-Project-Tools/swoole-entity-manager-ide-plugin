package dev.smallproject.ide

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundCompletionCacheTest {
    @Test fun returnsImmediatelyAndRunsIndexerOnce() {
        val queue = mutableListOf<Runnable>()
        val loads = AtomicInteger()
        val ready = AtomicInteger()
        val cache = BackgroundCompletionCache(Executor { queue.add(it) })
        val load: () -> List<String> = { loads.incrementAndGet(); listOf("id", "name") }

        assertTrue(cache.complete("manager:fields", load) { ready.incrementAndGet() }.isEmpty())
        assertTrue(cache.complete("manager:fields", load) { ready.incrementAndGet() }.isEmpty())
        assertEquals(1, queue.size)
        assertEquals(0, loads.get(), "The expensive indexer must not run under the read action")
        queue.single().run()

        assertEquals(1, loads.get())
        assertEquals(1, ready.get())
        assertEquals(listOf("id", "name"), cache.complete("manager:fields", load) { ready.incrementAndGet() })
        assertEquals(1, loads.get())
        assertEquals(1, ready.get())
    }

    @Test fun failedIndexerIsSafeAndCanRetry() {
        val queue = mutableListOf<Runnable>()
        var now = 0L
        val cache = BackgroundCompletionCache(Executor { queue.add(it) }, nanoTime = { now })
        assertTrue(cache.complete("broken", { error("index error") }) { error("unexpected callback") }.isEmpty())
        queue.removeAt(0).run()
        assertTrue(cache.complete("broken", { error("must remain cached") }) {}.isEmpty())
        assertTrue(queue.isEmpty())
        now = 11_000_000_000L
        assertTrue(cache.complete("broken", { listOf("recovered") }) {}.isEmpty())
        assertEquals(1, queue.size)
        queue.single().run()
        assertEquals(listOf("recovered"), cache.complete("broken", { error("should be cached") }) {})
    }

    @Test fun overloadedWorkerDoesNotBlockCompletionCaller() {
        val cache = BackgroundCompletionCache(Executor { throw java.util.concurrent.RejectedExecutionException() })
        assertTrue(cache.complete("busy", { listOf("id") }) {}.isEmpty())
        assertTrue(cache.complete("busy", { listOf("id") }) {}.isEmpty())
    }

    @Test fun ignoresHugeOrUnbalancedPhpSource() {
        assertEquals(null, QueryContextResolver.resolve("(".repeat(129) + "->getFieldForCondition('"))
        assertEquals(null, QueryContextResolver.resolve(" ".repeat(QueryContextResolver.MAX_SOURCE_LENGTH + 1)))
        assertEquals(null, QueryContextResolver.resolve(
            "\u0024query->where()->firstCondition(var1: 'person.deletedAt IS NULL"
        ))
    }
}
