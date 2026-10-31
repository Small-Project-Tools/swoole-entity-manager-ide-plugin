package dev.smallproject.ide

import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Project-installed CLI takes precedence; otherwise use the core packaged with the plugin. */
object SemIdeService {
    private data class CacheEntry(val expiresAtNs: Long, val labels: List<String>)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlNs = TimeUnit.SECONDS.toNanos(3)

    /** Exposed for pure file-system tests without starting an IntelliJ instance. */
    internal fun findCli(projectRoot: File, pluginJar: File?): File? {
        val installed = listOf(File(projectRoot, "vendor/bin/sem-ide"), File(projectRoot, "bin/sem-ide"))
            .firstOrNull { it.isFile }
        if (installed != null) return installed
        val root = pluginJar?.parentFile?.parentFile ?: return null
        return File(root, "core/bin/sem-ide").takeIf { it.isFile }
    }

    fun complete(project: Project, context: QueryContext): List<String> {
        val root = project.basePath?.let(::File) ?: return emptyList()
        val jar = runCatching { File(SemIdeService::class.java.protectionDomain.codeSource.location.toURI()) }
            .getOrNull()
        val cli = findCli(root, jar) ?: return emptyList()
        val key = root.absolutePath + "\u0000" + cli.absolutePath + "\u0000" + SemIdeClient.encodeRequest(context)
        cache[key]?.takeIf { System.nanoTime() - it.expiresAtNs < 0 }?.let { return it.labels }
        val labels = SemIdeClient.complete(root, cli, context)
        if (cache.size > 200) cache.clear()
        cache[key] = CacheEntry(System.nanoTime() + ttlNs, labels)
        return labels
    }
}
