package dev.smallproject.ide

import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Thin project adapter around the testable common CLI protocol. */
object SemIdeService {
    private data class CacheEntry(val expiresAtNs: Long, val labels: List<String>)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlNs = TimeUnit.SECONDS.toNanos(3)

    fun complete(project: Project, context: QueryContext): List<String> {
        val rootPath = project.basePath ?: return emptyList()
        val root = File(rootPath)
        val cli = sequenceOf(File(root, "vendor/bin/sem-ide"), File(root, "bin/sem-ide"))
            .firstOrNull { it.isFile } ?: return emptyList()
        val request = SemIdeClient.encodeRequest(context)
        val key = "$rootPath\u0000$request"
        val now = System.nanoTime()
        cache[key]?.takeIf { now - it.expiresAtNs < 0 }?.let { return it.labels }
        val labels = SemIdeClient.complete(root, cli, context)
        if (cache.size > 200) cache.clear()
        cache[key] = CacheEntry(System.nanoTime() + ttlNs, labels)
        return labels
    }
}
