package dev.smallproject.ide

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Non-sensitive diagnostics about IntelliJ's completion contribution. */
internal object SmallCompletionTelemetry {
    private val invocations = AtomicLong()
    private val matchedContexts = AtomicLong()
    private val contributedCandidates = AtomicInteger()

    @Volatile private var mostRecentContext = "unresolved"

    fun recordContext(context: QueryContext?) {
        invocations.incrementAndGet()
        if (context != null) matchedContexts.incrementAndGet()
        mostRecentContext = context?.kind ?: "unresolved"
    }

    fun recordCandidates(count: Int) {
        contributedCandidates.set(count)
    }

    fun report(): String = buildString {
        appendLine("Contributor invocations: " + invocations.get())
        appendLine("Resolved invocation contexts: " + matchedContexts.get())
        appendLine("Last contributor context: " + mostRecentContext)
        appendLine("Last contributed candidates: " + contributedCandidates.get())
    }
}
