package dev.smallproject.ide

/**
 * Type conclusions depend only on the current PHP file and the method arguments.
 * This is safe during PhpStorm indexing (no PhpIndex or other global indices).
 */
internal object SemTypeInference {
    private const val QUERY = "Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\"
    private val builderTypes = mapOf(
        "createQueryBuilder" to "QueryBuilder",
        "createScallarQueryBuilder" to "ScallarRelationalQueryBuilder",
        "createStatsQueryBuilder" to "StatsQueryBuilder",
        "createUpdateBuilder" to "UpdateBuilder",
        "createDeleteBuilder" to "DeleteBuilder",
        "getFieldForCondition" to "Bean\\ConditionField",
        "where" to "Bean\\Bracket",
        "join" to "Bean\\JoinBuilder",
        "leftJoin" to "Bean\\LeftJoinBuilder",
        "rightJoin" to "Bean\\RightJoinBuilder",
        "innerJoin" to "Bean\\InnerJoinBuilder",
        "endWhere" to "QueryBuilder",
        "endJoin" to "QueryBuilder",
        "firstCondition" to "Bean\\Bracket",
        "andCondition" to "Bean\\Bracket",
        "orCondition" to "Bean\\Bracket",
        "xorCondition" to "Bean\\Bracket",
    )
    private val classReference = Regex("""^\s*(?:(?:managerClass|manager)\s*:\s*)?([\\\w]+)\s*::\s*class\s*$""")

    fun factoryResult(source: String, offset: Int, firstArgument: String): String? {
        val raw = classReference.matchEntire(firstArgument)?.groupValues?.get(1) ?: return null
        if (!raw.substringAfterLast('\\').endsWith("Manager")) return null
        return PhpNameResolver.fromSource(source, offset, raw)?.let { "\\$it" }
    }

    fun builderResult(method: String, source: String, offset: Int): String? {
        val name = builderTypes[method] ?: return null
        val before = source.take(offset.coerceIn(0, source.length))
        // Do not claim arbitrary third-party methods with matching names.
        if (!before.contains(Regex("""->\s*get\s*\(\s*(?:(?:managerClass|manager)\s*:\s*)?[\\\w]+Manager::class"""))
            && !before.contains(Regex("""@var\s+[\\\w]*Manager\s+\$\w+"""))) return null
        return "\\$QUERY$name"
    }
}
