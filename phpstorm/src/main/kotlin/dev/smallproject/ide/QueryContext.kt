package dev.smallproject.ide

data class QueryContext(
    val kind: String,
    val manager: String? = null,
    val rootAlias: String? = null,
    val alias: String? = null,
    val joins: List<Map<String, String>> = emptyList(),
    val parameters: List<String> = emptyList(),
    val typedPrefix: String = "",
)

/** Conservative PHP source analysis for query literal completion, without executing PHP. */
object QueryContextResolver {
    private val imports = Regex("""(?m)^\s*use\s+([\\\w]+)(?:\s+as\s+(\w+))?\s*;""")
    private val namespace = Regex("""\bnamespace\s+([\\\w]+)\s*;""")
    private val factory = Regex("""\$(\w+)\s*=\s*[^;]*?->\s*get\s*\(\s*(?:managerClass\s*:\s*)?([\\\w]+)::class""")
    private val annotated = Regex("""@var\s+([\\\w]+Manager)\s+\$(\w+)""")
    private val builder = Regex("""\$(\w+)\s*=\s*\$(\w+)\s*->\s*(createQueryBuilder|createScallarQueryBuilder|createStatsQueryBuilder|createUpdateBuilder|createDeleteBuilder)\s*\(""")
    private val directBuilder = Regex("""\$(\w+)\s*->\s*(createQueryBuilder|createScallarQueryBuilder|createStatsQueryBuilder|createUpdateBuilder|createDeleteBuilder)\s*\(""")
    private val methodBeforeParen = Regex("""->\s*([A-Za-z_]\w*)\s*$""")
    private val queryCall = Regex("""->\s*(join|leftJoin|innerJoin|rightJoin)\s*\(""")
    private val placeholder = Regex("""['"]:([a-zA-Z_]\w*)['"]""")
    private data class OpenCall(val method: String, val open: Int)

    fun resolve(sourceBeforeCaret: String): QueryContext? {
        val call = activeCall(sourceBeforeCaret) ?: return null
        val parts = splitArgs(sourceBeforeCaret.substring(call.open + 1))
        val current = parts.lastOrNull()?.trim() ?: ""
        val named = Regex("""^(\w+)\s*:\s*(.*)$""", RegexOption.DOT_MATCHES_ALL).matchEntire(current)
        val role = methodArgumentRole(call.method, named?.groupValues?.get(1), parts.size - 1) ?: return null
        val literal = named?.groupValues?.get(2) ?: current
        val quote = literal.trimStart().firstOrNull()
        if (quote != '\'' && quote != '"') return null
        val typed = literal.trimStart().drop(1)
        if (typed.contains(quote)) return null
        val before = sourceBeforeCaret.substring(0, call.open)
        val importMap = imports.findAll(before).associate { m ->
            val full = m.groupValues[1].trimStart('\\')
            m.groupValues[2].ifBlank { full.substringAfterLast('\\') } to full
        }
        val currentNamespace = namespace.findAll(before).lastOrNull()?.groupValues?.get(1)
        fun fqcn(value: String): String {
            val cleaned = value.trimStart('\\')
            return if (value.startsWith("\\")) cleaned else importMap[cleaned]
                ?: if ('\\' in cleaned || currentNamespace == null) cleaned else "$currentNamespace\\$cleaned"
        }
        val managers = mutableMapOf<String, String>()
        for (m in annotated.findAll(before)) managers[m.groupValues[2]] = fqcn(m.groupValues[1])
        for (m in factory.findAll(before)) managers[m.groupValues[1]] = fqcn(m.groupValues[2])
        val builders = mutableMapOf<String, Pair<String, String?>>()
        for (m in builder.findAll(before)) {
            val manager = managers[m.groupValues[2]] ?: continue
            builders[m.groupValues[1]] = manager to builderAlias(before, m.range.last + 1)
        }
        val start = maxOf(before.lastIndexOf(';'), before.lastIndexOf('{'), before.lastIndexOf('}')) + 1
        val expression = before.substring(start)
        val inline = directBuilder.findAll(expression).lastOrNull()
        val target = if (inline != null) {
            managers[inline.groupValues[1]]?.let { it to builderAlias(expression, inline.range.last + 1) }
        } else {
            val activeVariable = Regex("""\$(\w+)\s*->""").find(expression)?.groupValues?.get(1)
            if (activeVariable != null) builders[activeVariable] ?: managers[activeVariable]?.let { it to null } else null
        } ?: return null

        val joins = collectJoins(expression)
        val relationSourceAlias = if (role == "relation") {
            val first = parts.firstOrNull()?.trim()?.substringAfter(':')?.trim().orEmpty()
            Regex("""^(['"])([^'"]+)\1$""").matchEntire(first)?.groupValues?.get(2)
        } else null
        val parameters = if (role == "parameter") placeholder.findAll(before)
            .map { it.groupValues[1] }.distinct().sorted().toList() else emptyList()
        return QueryContext(
            kind = role,
            manager = target.first,
            rootAlias = target.second,
            alias = relationSourceAlias,
            joins = joins,
            parameters = parameters,
            typedPrefix = typed,
        )
    }

    /** Argument names/positions correspond to the Core 3 method signatures. */
    internal fun methodArgumentRole(method: String, named: String?, index: Int): String? {
        val signature = when (method) {
            "getFieldForCondition" -> listOf("fieldName" to "field", "entityAlias" to "alias")
            "addOrderBy" -> listOf("fieldName" to "field", "entityAlias" to "alias", "sens" to "")
            "addFieldToUpdate" -> listOf("field" to "field", "update" to "")
            "join", "leftJoin", "rightJoin", "innerJoin" -> listOf("fromAlias" to "alias", "relationAlias" to "relation", "alias" to "", "type" to "")
            "setParameter" -> listOf("paramName" to "parameter", "value" to "")
            "select", "addSelect" -> listOf("fields" to "projection", "entityAlias" to "alias")
            "count", "countDistinct", "sum", "avg", "min", "max", "stddev", "stddevPop", "stddevSamp", "varPop", "varSamp", "groupConcat" -> listOf("field" to "projection")
            else -> return null
        }
        return (if (named != null) signature.firstOrNull { it.first == named } else signature.getOrNull(index))?.second?.takeIf { it.isNotBlank() }
    }

    private fun activeCall(source: String): OpenCall? {
        val stack = java.util.ArrayDeque<Int>()
        var quote: Char? = null
        var escape = false
        for ((i, c) in source.withIndex()) {
            if (quote != null) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == quote) quote = null
                continue
            }
            when (c) {
                '\'', '"' -> quote = c
                '(' -> stack.addLast(i)
                ')' -> if (stack.isNotEmpty()) stack.removeLast()
            }
        }
        for (at in stack.toList().asReversed()) {
            val method = methodBeforeParen.find(source.substring(0, at))?.groupValues?.get(1) ?: continue
            if (methodArgumentRole(method, null, 0) != null) return OpenCall(method, at)
        }
        return null
    }

    private fun builderAlias(text: String, from: Int): String? {
        val end = closingParen(text, from - 1) ?: return null
        val args = splitArgs(text.substring(from, end))
        val raw = args.firstOrNull()?.trim()?.substringAfter(':')?.trim().orEmpty()
        return Regex("""^(['"])([^'"]+)\1$""").matchEntire(raw)?.groupValues?.get(2)
    }

    private fun collectJoins(text: String): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        for (match in queryCall.findAll(text)) {
            val open = match.range.last
            val end = closingParen(text, open) ?: continue
            val args = splitArgs(text.substring(open + 1, end))
            val named = args.map { it.trim() }.filter { it.contains(':') }.associate {
                it.substringBefore(':').trim() to it.substringAfter(':').trim()
            }
            fun arg(n: String, position: Int): String? {
                val value = named[n] ?: args.getOrNull(position)?.trim() ?: return null
                return Regex("""^(['"])([^'"]+)\1$""").matchEntire(value)?.groupValues?.get(2)
            }
            val from = arg("fromAlias", 0) ?: continue
            val relation = arg("relationAlias", 1) ?: continue
            val alias = arg("alias", 2) ?: relation
            result.add(mapOf("fromAlias" to from, "relation" to relation, "alias" to alias))
        }
        return result
    }

    private fun closingParen(text: String, opening: Int): Int? {
        var depth = 0
        var quote: Char? = null
        var escape = false
        for (i in opening until text.length) {
            val c = text[i]
            if (quote != null) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == quote) quote = null
                continue
            }
            when (c) {
                '\'', '"' -> quote = c
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return i }
            }
        }
        return null
    }

    private fun splitArgs(source: String): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0
        var nested = 0
        var quote: Char? = null
        var escaped = false
        for ((i, c) in source.withIndex()) {
            if (quote != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == quote) quote = null
                continue
            }
            when (c) {
                '\'', '"' -> quote = c
                '(', '[', '{' -> nested++
                ')', ']', '}' -> nested--
                ',' -> if (nested == 0) { pieces.add(source.substring(start, i)); start = i + 1 }
            }
        }
        pieces.add(source.substring(start))
        return pieces
    }
}
