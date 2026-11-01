package dev.smallproject.ide

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes

/** Read-only, local aggregate validation. Never prints PHP code, paths, or domain identifiers. */
object ProjectCoverageAudit {
    private val calls = Regex(
        """->\s*(getFieldForCondition|firstCondition|andCondition|orCondition|xorCondition|addOrderBy|setParameter|leftJoin|innerJoin|rightJoin|join|select|addSelect|count|sum|avg|min|max)\s*\(""",
    )
    private val excluded = setOf("vendor", ".git", ".idea", ".vscode", "node_modules", "var", "cache", "build", "dist")

    data class Counts(
        val files: Int, val managers: Int, val entities: Int, val fields: Int,
        val relations: Int, val dangling: Int, val calls: Int, val resolved: Int,
        val mapped: Int, val suggested: Int, val errors: Int,
        val indexMillis: Long, val auditMillis: Long,
        val byMethod: Map<String, Int>, val resolvedByMethod: Map<String, Int>,
        val suggestedByMethod: Map<String, Int>,
    ) {
        fun summary(): String = buildString {
            appendLine("Native PHP-free completion audit (aggregate only)")
            appendLine("PHP files: $files | managers: $managers | entities: $entities")
            appendLine("Fields: $fields | relations: $relations | unmapped relations: $dangling")
            appendLine("Query calls: $calls | contexts: $resolved | mapped: $mapped")
            appendLine("Calls with suggestions: $suggested | exceptions: $errors")
            appendLine("Index time ms: $indexMillis | context audit ms: $auditMillis")
            for ((method, count) in byMethod.toSortedMap()) {
                appendLine(method + ": " + count + " calls, " +
                    (resolvedByMethod[method] ?: 0) + " resolved, " +
                    (suggestedByMethod[method] ?: 0) + " with suggestions")
            }
        }
    }

    fun audit(root: File): Counts {
        require(root.isDirectory) { "An existing local project directory is required" }
        val started = System.nanoTime()
        val index = NativeProjectScanner.scan(root)
        val indexMs = (System.nanoTime() - started) / 1_000_000
        val relations = index.entities.values.sumOf { it.relations.size }
        val dangling = index.entities.values.sumOf { entity ->
            entity.relations.values.count { it == null || it !in index.managers }
        }
        var files = 0
        var total = 0
        var resolved = 0
        var mapped = 0
        var suggested = 0
        var errors = 0
        val byMethod = sortedMapOf<String, Int>()
        val resolvedBy = sortedMapOf<String, Int>()
        val suggestedBy = sortedMapOf<String, Int>()
        val auditStarted = System.nanoTime()
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root.toPath() && dir.fileName.toString() in excluded) FileVisitResult.SKIP_SUBTREE
                else FileVisitResult.CONTINUE
            override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isRegularFile || attrs.size() > 2L * 1024 * 1024 ||
                    !path.fileName.toString().endsWith(".php", ignoreCase = true)) return FileVisitResult.CONTINUE
                val text = runCatching { Files.readString(path, StandardCharsets.UTF_8) }
                    .getOrNull() ?: return FileVisitResult.CONTINUE
                files++
                // Only executable PHP calls belong in coverage metrics. Comments,
                // string literals and heredocs are source text, not method calls.
                val executable = PhpExecutableSourceMask.mask(text)
                for (match in calls.findAll(executable)) {
                    total++
                    val method = match.groupValues[1]
                    byMethod[method] = (byMethod[method] ?: 0) + 1
                    val context = runCatching { QueryContextResolver.resolve(text.substring(0, match.range.last + 1)) }
                        .onFailure { errors++ }.getOrNull() ?: continue
                    resolved++
                    resolvedBy[method] = (resolvedBy[method] ?: 0) + 1
                    val manager = context.manager?.trimStart('\\')
                    if (manager != null && index.managers.containsKey(manager)) mapped++
                    val candidates = runCatching { index.complete(context) }
                        .onFailure { errors++ }.getOrNull()
                    if (!candidates.isNullOrEmpty()) {
                        suggested++
                        suggestedBy[method] = (suggestedBy[method] ?: 0) + 1
                    }
                }
                return FileVisitResult.CONTINUE
            }
        })
        return Counts(files, index.managers.size, index.entities.size,
            index.entities.values.sumOf { it.fields.size }, relations, dangling,
            total, resolved, mapped, suggested, errors, indexMs,
            (System.nanoTime() - auditStarted) / 1_000_000,
            byMethod, resolvedBy, suggestedBy)
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: ProjectCoverageAudit <local-directory>" }
        print(audit(File(args[0])).summary())
    }
}
