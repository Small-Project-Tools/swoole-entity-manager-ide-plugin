package dev.smallproject.ide

/**
 * Conservative inference of a local zero-argument manager accessor, e.g.
 * $this->catalogManager() returning $this->factory->get(CatalogManager::class).
 *
 * Unknown branches, unrelated factories, recursion, and other PHP classes
 * intentionally fail closed instead of overriding PhpStorm's native types.
 */
internal object SemManagerAccessorInference {
    private val container = Regex("""\b(?:class|trait)\s+[A-Za-z_]\w*[^{};]*\{""")
    private val method = Regex(
        """\bfunction\s+(?:&\s*)?([A-Za-z_]\w*)\s*\(\s*\)\s*(?::\s*\??[\\\w|&]+)?\s*\{""",
    )
    private val returns = Regex("""\breturn\b([^;]*);""", RegexOption.DOT_MATCHES_ALL)
    private val getCall = Regex(
        """^\s*(\$(?:this->)?\w+)\s*->\s*get\s*\(([^;]*)\)\s*$""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val delegate = Regex("""^\s*\$this->(\w+)\s*\(\s*\)\s*$""")
    private val variable = Regex("""^\s*\$(\w+)\s*$""")
    private val assignment = Regex(
        """\$([A-Za-z_]\w*)\s*=\s*(\$(?:this->)?\w+)\s*->\s*get\s*\(([^;]*)\)\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val assignments = Regex("""\$([A-Za-z_]\w*)\s*=(?!=)""")

    fun infer(source: String, offset: Int, accessor: String): String? {
        if (source.length > QueryContextResolver.MAX_SOURCE_LENGTH || offset !in 0..source.length) return null
        if (!Regex("""^[A-Za-z_]\w*$""").matches(accessor) || !source.contains("->get")) return null
        val executable = PhpExecutableSourceMask.mask(source)
        val containing = container.findAll(executable).mapNotNull {
            val end = matchingBrace(executable, it.range.last) ?: return@mapNotNull null
            if (offset > it.range.last && offset < end) Pair(it.range.last, end) else null
        }.minByOrNull { it.second - it.first } ?: return null

        val bodies = mutableMapOf<String, Pair<Int, String>>()
        for (found in method.findAll(executable, containing.first + 1)) {
            if (found.range.first >= containing.second) break
            if (nesting(executable, containing.first, found.range.first) != 1) continue
            val end = matchingBrace(executable, found.range.last) ?: continue
            if (end > containing.second) continue
            bodies[found.groupValues[1].lowercase()] =
                found.range.last + 1 to executable.substring(found.range.last + 1, end)
        }

        fun resolve(name: String, visited: Set<String>): String? {
            val key = name.lowercase()
            if (key in visited || visited.size >= 5) return null
            val (start, body) = bodies[key] ?: return null
            val matches = returns.findAll(body).toList()
            if (matches.isEmpty() || matches.size != Regex("""\breturn\b""").findAll(body).count()) return null

            val inferred = matches.map { match ->
                val expression = match.groupValues[1].trim()
                val delegated = delegate.matchEntire(expression)
                if (delegated != null) return@map resolve(delegated.groupValues[1], visited + key) ?: return null

                val direct = getCall.matchEntire(expression)
                val call = if (direct != null) direct else {
                    val v = variable.matchEntire(expression)?.groupValues?.get(1) ?: return null
                    val before = body.substring(0, match.range.first)
                    if (assignments.findAll(before).count { it.groupValues[1] == v } != 1) return null
                    assignment.findAll(before).filter { it.groupValues[1] == v }.singleOrNull()
                        ?: return null
                }
                val receiver = call.groupValues[if (direct != null) 1 else 2]
                val args = call.groupValues[if (direct != null) 2 else 3].split(',')
                val argument = SemTypeInference.managerArgument(args) ?: return null
                SemTypeInference.factoryResult(source, start + match.range.first, argument, receiver)
                    ?: return null
            }.distinct()
            return inferred.singleOrNull()
        }
        return resolve(accessor, emptySet())
    }

    private fun matchingBrace(source: String, opening: Int): Int? {
        var level = 0
        for (i in opening until source.length) when (source[i]) {
            '{' -> level++
            '}' -> if (--level == 0) return i
        }
        return null
    }

    private fun nesting(source: String, opening: Int, end: Int): Int {
        var level = 0
        for (i in opening until end) when (source[i]) {
            '{' -> level++
            '}' -> level--
        }
        return level
    }
}
