package dev.smallproject.ide

/**
 * Dynamic factory return inference only. Methods such as where(), leftJoin()
 * and getFieldForCondition() already declare accurate return types in Core 3.
 * Never override those types based on unrelated factory calls in the file.
 */
internal object SemTypeInference {
    private val classReference = Regex("""^\s*(?:(?:managerClass|manager)\s*:\s*)?([\\\w]+)\s*::\s*class\s*$""")
    private val namedManager = Regex("""^\s*(?:managerClass|manager)\s*:(?!:)""")
    private val namedArgument = Regex("""^\s*[A-Za-z_]\w*\s*:(?!:)""")
    private val variable = Regex("""^\$(?:(?:this)->)?([A-Za-z_]\w*)$""")
    private val supportedFactoryTypes = setOf(
        "Small\\SwooleEntityManager\\Factory\\EntityManagerFactory",
        "Small\\SwooleEntityManagerBundle\\Factory\\EntityManagerFactory",
        "Small\\SwooleEntityManagerBundle\\Contract\\EntityManagerFactoryInterface",
    )
    private val typedVariable = Regex("""(?<![A-Za-z0-9_\\])([\\\w]+)\s+\$(\w+)\b""")

    fun managerArgument(arguments: List<String>): String? {
        val named = arguments.filter { namedManager.containsMatchIn(it) }
        if (named.size > 1 || (named.isNotEmpty() && arguments.firstOrNull()?.let { !namedArgument.containsMatchIn(it) } == true)) return null
        return named.singleOrNull() ?: arguments.firstOrNull()?.takeUnless {
            namedArgument.containsMatchIn(it)
        }
    }


    /**
     * PhpStorm PSI removes argument names from MethodReference.parameters.
     * Use the original ParameterList text to retain managerClass and manager
     * when named arguments are reordered. Nested commas are not separators.
     */
    fun managerArgumentFromParameterList(parameterList: String): String? {
        val raw = parameterList.trim()
        if (raw.length < 2 || raw.first() != '(' || raw.last() != ')') return null
        val inside = raw.substring(1, raw.length - 1)
        val parts = mutableListOf<String>()
        var start = 0
        var nesting = 0
        var quote: Char? = null
        var escaped = false
        for ((index, value) in inside.withIndex()) {
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    value == '\\' -> escaped = true
                    value == quote -> quote = null
                }
                continue
            }
            when (value) {
                '\'', '"' -> quote = value
                '(', '[', '{' -> nesting++
                ')', ']', '}' -> {
                    nesting--
                    if (nesting < 0) return null
                }
                ',' -> if (nesting == 0) {
                    parts.add(inside.substring(start, index))
                    start = index + 1
                }
            }
        }
        if (nesting != 0 || quote != null) return null
        parts.add(inside.substring(start))
        return managerArgument(parts)?.trim()?.takeIf { classReference.matchEntire(it) != null }
    }

    fun factoryResult(source: String, offset: Int, argument: String, receiver: String): String? {
        if (source.length > QueryContextResolver.MAX_SOURCE_LENGTH || offset !in 0..source.length) return null
        val match = classReference.matchEntire(argument) ?: return null
        val raw = match.groupValues[1]
        val managerClass = PhpNameResolver.fromSource(source, offset, raw) ?: return null
        if (!managerClass.substringAfterLast('\\').endsWith("Manager")) return null
        if (!isFactoryReceiver(receiver, source.take(offset))) return null
        return "\\" + managerClass
    }

    internal fun isFactoryReceiver(receiver: String, before: String): Boolean {
        val name = variable.matchEntire(receiver.trim())?.groupValues?.get(1) ?: return false
        // Resolve aliased imports before accepting a typed receiver. Other factories
        // with a similar class name must not contaminate Small type inference.
        val types = typedVariable.findAll(before)
            .filter { it.groupValues[2] == name }
            .mapNotNull { PhpNameResolver.fromSource(before, before.length, it.groupValues[1]) }
            .toList()
        if (types.isNotEmpty()) return types.last() in supportedFactoryTypes
        // Conventional untyped names are supported for legacy PHP projects.
        return name.equals("factory", ignoreCase = true) ||
            name.equals("entityManagerFactory", ignoreCase = true) ||
            name.equals("managerFactory", ignoreCase = true)
    }
}
