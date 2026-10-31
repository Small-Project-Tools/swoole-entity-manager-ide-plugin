package dev.smallproject.ide

import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The core CLI emits JSON. We parse only the label/kind pairs and never run user PHP classes.
 * Each invocation has a bounded process lifetime.
 */
object SemIdeService {
    private val label = Regex(""""label"\s*:\s*"((?:\\.|[^"\\])*)"""")
    fun complete(project: Project, context: QueryContext): List<String> {
        val root = project.basePath ?: return emptyList()
        val binary = sequenceOf(
            File(root, "vendor/bin/sem-ide"),
            File(root, "bin/sem-ide")
        ).firstOrNull { it.isFile } ?: return emptyList()
        val request = buildString {
            append("{\"kind\":\"").append(context.kind).append("\"")
            context.manager?.let { append(",\"manager\":\"").append(escape(it)).append("\"") }
            context.rootAlias?.let { append(",\"rootAlias\":\"").append(escape(it)).append("\"") }
            context.alias?.let { append(",\"alias\":\"").append(escape(it)).append("\"") }
            append(",\"joins\":[")
            context.joins.forEachIndexed { i, join ->
                if (i > 0) append(',')
                append('{')
                join.entries.forEachIndexed { j, entry ->
                    if (j > 0) append(',')
                    append("\"").append(entry.key).append("\":\"").append(escape(entry.value)).append("\"")
                }
                append('}')
            }
            append("]}")
        }
        return try {
            val process = ProcessBuilder("php", binary.absolutePath, "complete", root, request)
                .directory(File(root)).redirectErrorStream(false).start()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return emptyList()
            }
            if (process.exitValue() != 0) return emptyList()
            label.findAll(process.inputStream.bufferedReader().readText())
                .map { it.groupValues[1].replace("\\\\", "\\").replace("\\\"", "\"") }.toList()
        } catch (_: Exception) { emptyList() }
    }
    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
