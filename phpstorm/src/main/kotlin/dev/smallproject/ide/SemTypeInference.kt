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
