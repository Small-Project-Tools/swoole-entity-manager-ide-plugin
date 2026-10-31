package dev.smallproject.ide

/**
 * Bounded source analysis, intentionally conservative. No PHP code is executed.
 * The preceding local variable assignment must name an explicit manager class.
 */
data class QueryContext(val kind: String, val manager: String? = null, val rootAlias: String? = null,
                        val alias: String? = null, val joins: List<Map<String, String>> = emptyList())

object QueryContextResolver {
    private val factory = Regex("""\$(\w+)\s*=\s*\$\w+\s*->\s*get\s*\(\s*([\\\w]+)::class\s*\)""")
    private val builder = Regex("""\$(\w+)\s*=\s*\$(\w+)\s*->\s*create(?:Scallar|Stats|Update|Delete)?QueryBuilder\s*\(\s*(?:['"]([^'"]+)['"])?""")
    private val joinsRegex = Regex("""->\s*(?:innerJoin|leftJoin|rightJoin|join)\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"](?:\s*,\s*['"]([^'"]+)['"])?""")
    private val phpDoc = Regex("""@var\s+([\\w]+Manager)\s+\$(\w+)""")
    private val methods = mapOf(
        "getFieldForCondition" to "field", "addOrderBy" to "field", "addFieldToUpdate" to "field",
        "join" to "relation", "innerJoin" to "relation", "leftJoin" to "relation", "rightJoin" to "relation",
        "setParameter" to "parameter"
    )
    fun resolve(prefix: String): QueryContext? {
        val invocation = Regex("""(?:\$(\w+)\s*->\s*)?(getFieldForCondition|addOrderBy|addFieldToUpdate|join|innerJoin|leftJoin|rightJoin|setParameter)\s*\(([^()]*)$""").findAll(prefix).lastOrNull() ?: return null
        val receiver = invocation.groupValues[1]
        val method = invocation.groupValues[2]
        val args = invocation.groupValues[3]
        val argNo = args.count { it == ',' }
        val kind = when (method) {
            "join", "innerJoin", "leftJoin", "rightJoin" -> if (argNo == 0) "alias" else if (argNo == 1) "relation" else return null
            "getFieldForCondition", "addOrderBy" -> if (argNo == 0) "field" else if (argNo == 1) "alias" else return null
            "addFieldToUpdate" -> if (argNo == 0) "field" else return null
            "setParameter" -> if (argNo == 0) "parameter" else return null
            else -> return null
        }
        val managerVars = mutableMapOf<String, String>()
        val imports = Regex("""use\s+([\\\w]+Manager)(?:\s+as\s+(\w+))?\s*;""")
            .findAll(prefix).associate { match ->
                val fqn = match.groupValues[1].trimStart('\\')
                val shortName = match.groupValues[2].ifBlank { fqn.substringAfterLast('\\') }
                shortName to fqn
            }
        fun resolveManager(name: String): String {
            val cleaned = name.trimStart('\\')
            return imports[cleaned] ?: cleaned
        }
        for (m in factory.findAll(prefix)) managerVars[m.groupValues[1]] = resolveManager(m.groupValues[2])
        for (m in phpDoc.findAll(prefix)) managerVars[m.groupValues[2]] = resolveManager(m.groupValues[1])
        val builders = mutableMapOf<String, Pair<String, String?>>()
        for (m in builder.findAll(prefix)) {
            val manager = managerVars[m.groupValues[2]] ?: continue
            builders[m.groupValues[1]] = manager to m.groupValues[3].ifBlank { null }
        }
        val pair = builders[receiver] ?: managerVars[receiver]?.let { it to null } ?: return null
        // Only join calls on this query variable before the invocation can define aliases.
        val joins = mutableListOf<Map<String, String>>()
        val before = prefix.substring(0, invocation.range.first)
        for (m in joinsRegex.findAll(before)) {
            joins.add(mapOf("fromAlias" to m.groupValues[1], "relation" to m.groupValues[2],
                "alias" to m.groupValues[3].ifBlank { m.groupValues[2] }))
        }
        val explicitAlias = if (kind == "field" && argNo == 0) {
            Regex("""getFieldForCondition\s*\(\s*['"][^'"]+['"]\s*,\s*['"]([^'"]+)['"]""")
                .findAll(before).lastOrNull()?.groupValues?.get(1)
        } else null
        return QueryContext(kind, pair.first, pair.second, explicitAlias, joins)
    }
}
