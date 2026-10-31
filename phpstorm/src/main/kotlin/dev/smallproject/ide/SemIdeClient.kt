package dev.smallproject.ide


/** Runs the offline PHP indexer outside the IDE read action with bounded time and output. */
object SemIdeClient {
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
