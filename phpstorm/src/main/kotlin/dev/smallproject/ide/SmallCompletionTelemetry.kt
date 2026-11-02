package dev.smallproject.ide

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Non-sensitive diagnostics about IntelliJ's completion contribution. */
internal object SmallCompletionTelemetry {
    private val invocations = AtomicLong()
    private val matchedContexts = AtomicLong()
    private val contributedCandidates = AtomicInteger()

    private val autoPopupRequests = AtomicLong()
    private val autoPopupOverrides = AtomicLong()

    @Volatile private var lastAutoPopupRequest = "none"
    @Volatile private var mostRecentContext = "unresolved"

    fun recordContext(context: QueryContext?) {
        invocations.incrementAndGet()
        if (context != null) matchedContexts.incrementAndGet()
        mostRecentContext = context?.kind ?: "unresolved"
    }

    fun recordCandidates(count: Int) {
        contributedCandidates.set(count)
    }

    fun recordAutoPopupRequest(kind: String, character: Char) {
        autoPopupRequests.incrementAndGet()
        lastAutoPopupRequest = "$kind ($character)"
    }

    fun recordAutoPopupOverride() {
        autoPopupOverrides.incrementAndGet()
    }

    fun report(): String = buildString {
        appendLine("Contributor invocations: " + invocations.get())
        appendLine("Resolved invocation contexts: " + matchedContexts.get())
        appendLine("Last contributor context: " + mostRecentContext)
        appendLine("Last contributed candidates: " + contributedCandidates.get())
        appendLine("Auto-popup requests: " + autoPopupRequests.get())
        appendLine("Last auto-popup request: " + lastAutoPopupRequest)
        appendLine("Bare qualified alias overrides: " + autoPopupOverrides.get())
    }
}
