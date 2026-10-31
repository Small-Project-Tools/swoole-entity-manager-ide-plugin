package dev.smallproject.ide

/** A conservative query context extracted from the PHP file before the caret. */
data class QueryContext(
    val kind: String,
    val manager: String? = null,
    val rootAlias: String? = null,
    val alias: String? = null,
    val joins: List<Map<String, String>> = emptyList(),
    val parameters: List<String> = emptyList(),
)

/** No PHP application classes are loaded or executed by this resolver. */
object QueryContextResolver {
    private val imports = Regex("""(?m)^\s*use\s+([\\\w]+Manager)(?:\s+as\s+(\w+))?\s*;""")
    private val factory = Regex("""\$(\w+)\s*=\s*\$\w+\s*->\s*get\s*\(\s*([\\\w]+)::class\s*(?:,\s*(?:true|false))?\s*\)""")
    private val phpDoc = Regex("""@var\s+([\\\w]+Manager)\s+\$(\w+)""")
    private val builder = Regex("""\$(\w+)\s*=\s*\$(\w+)\s*->\s*create(?:Scallar|Stats|Update|Delete)?QueryBuilder\s*\(\s*(?:['"]([^'"]+)['"])?""")
    private val invocation = Regex("""\$(\w+)\s*->\s*(getFieldForCondition|addOrderBy|addFieldToUpdate|join|innerJoin|leftJoin|rightJoin|setParameter)\s*\(([^()]*)$""")
    private val join = Regex("""\$(\w+)\s*->\s*(?:innerJoin|leftJoin|rightJoin|join)\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"](?:\s*,\s*['"]([^'"]+)['"])?""")
    private val placeholder = Regex("""['"]:([a-zA-Z_][a-zA-Z0-9_]*)['"]""")

    fun resolve(sourceBeforeCaret: String): QueryContext? {
        val match = invocation.findAll(sourceBeforeCaret).lastOrNull() ?: return null
        val receiver = match.groupValues[1]
        val method = match.groupValues[2]
        val args = match.groupValues[3]
        val argNo = argumentPosition(args)
        val kind = when (method) {
            "join", "innerJoin", "leftJoin", "rightJoin" -> when (argNo) { 0 -> "alias"; 1 -> "relation"; else -> return null }
            "getFieldForCondition", "addOrderBy" -> when (argNo) { 0 -> "field"; 1 -> "alias"; else -> return null }
            "addFieldToUpdate" -> if (argNo == 0) "field" else return null
            "setParameter" -> if (argNo == 0) "parameter" else return null
            else -> return null
        }
        val before = sourceBeforeCaret.substring(0, match.range.first)
        val importMap = imports.findAll(before).associate { m ->
            val fqn = m.groupValues[1].trimStart('\\')
            m.groupValues[2].ifBlank { fqn.substringAfterLast('\\') } to fqn
        }
        fun className(name: String): String = importMap[name.trimStart('\\')] ?: name.trimStart('\\')
        val managers = mutableMapOf<String, String>()
        for (m in factory.findAll(before)) managers[m.groupValues[1]] = className(m.groupValues[2])
        for (m in phpDoc.findAll(before)) managers[m.groupValues[2]] = className(m.groupValues[1])
        val builders = mutableMapOf<String, Pair<String, String?>>()
        for (m in builder.findAll(before)) {
            val manager = managers[m.groupValues[2]] ?: continue
            builders[m.groupValues[1]] = manager to m.groupValues[3].ifBlank { null }
        }
        val query = builders[receiver] ?: managers[receiver]?.let { it to null } ?: return null
        val joins = join.findAll(before).filter { it.groupValues[1] == receiver }.map { m ->
            mapOf("fromAlias" to m.groupValues[2], "relation" to m.groupValues[3],
                "alias" to m.groupValues[4].ifBlank { m.groupValues[3] })
        }.toList()
        val parameters = if (kind == "parameter") placeholder.findAll(before)
            .map { it.groupValues[1] }.distinct().sorted().toList() else emptyList()
        return QueryContext(kind, query.first, query.second, joins = joins, parameters = parameters)
    }

    /** Commas inside quoted PHP strings do not select a subsequent method argument. */
    private fun argumentPosition(args: String): Int {
        var quote = '\u0000'
        var escaped = false
        var commas = 0
        for (c in args) {
            if (escaped) { escaped = false; continue }
            if (quote != '\u0000') {
                if (c == '\\') escaped = true
                else if (c == quote) quote = '\u0000'
            } else if (c == '\'' || c == '"') quote = c
            else if (c == ',') commas++
        }
        return commas
    }
}
