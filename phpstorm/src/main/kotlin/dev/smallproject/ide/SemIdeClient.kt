package dev.smallproject.ide

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Runs the offline PHP indexer outside the IDE read action with bounded time and output. */
object SemIdeClient {
    private const val MAX_OUTPUT_BYTES = 512 * 1024
    private val labelPattern = Regex(""""label"\s*:\s*"((?:\\.|[^"\\])*)"""")

    fun encodeRequest(context: QueryContext): String = buildString {
        append("{\"kind\":\"").append(escape(context.kind)).append('"')
        context.manager?.let { append(",\"manager\":\"").append(escape(it)).append('"') }
        context.rootAlias?.let { append(",\"rootAlias\":\"").append(escape(it)).append('"') }
        context.alias?.let { append(",\"alias\":\"").append(escape(it)).append('"') }
        append(",\"joins\":[")
        context.joins.forEachIndexed { index, join ->
            if (index > 0) append(',')
            append('{')
            join.entries.forEachIndexed { inner, (key, value) ->
                if (inner > 0) append(',')
                append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"')
            }
            append('}')
        }
        append("],\"parameters\":[")
        context.parameters.forEachIndexed { index, name ->
            if (index > 0) append(',')
            append('"').append(escape(name)).append('"')
        }
        append("]}")
    }

    fun parseLabels(json: String): List<String> {
        if (!json.trimStart().startsWith("[")) return emptyList()
        return labelPattern.findAll(json).map { m -> unescape(m.groupValues[1]) }.toList()
    }

    fun complete(root: File, binary: File, context: QueryContext, timeoutMs: Long = 5000): List<String> {
        if (!root.isDirectory || !binary.isFile) return emptyList()
        val output = Files.createTempFile("small-ide-", ".json")
        try {
            val process = ProcessBuilder("php", binary.absolutePath, "complete", root.absolutePath, encodeRequest(context))
                .directory(root)
                .redirectOutput(output.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(200, TimeUnit.MILLISECONDS)
                return emptyList()
            }
            if (process.exitValue() != 0 || Files.size(output) > MAX_OUTPUT_BYTES) return emptyList()
            return parseLabels(Files.readString(output, Charsets.UTF_8))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private fun escape(value: String) = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }
    private fun unescape(input: String): String = buildString {
        var i = 0
        while (i < input.length) {
            val c = input[i++]
            if (c != '\\' || i >= input.length) { append(c); continue }
            when (val n = input[i++]) {
                '\\', '/', '"' -> append(n)
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                'b' -> append('\b')
                'f' -> append('\u000c')
                'u' -> {
                    if (i + 4 <= input.length) {
                        val hex = input.substring(i, i + 4)
                        val point = hex.toIntOrNull(16)
                        if (point != null) { append(point.toChar()); i += 4 }
                    }
                }
            }
        }
    }
}
