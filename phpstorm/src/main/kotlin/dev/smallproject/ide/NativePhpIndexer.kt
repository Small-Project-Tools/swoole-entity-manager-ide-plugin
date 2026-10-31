package dev.smallproject.ide

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Read-only project metadata; neither PHP nor application code is executed. */
internal data class NativeEntity(val fields: Set<String>, val relations: Map<String, String?>)

internal data class NativeProjectIndex(
    val entities: Map<String, NativeEntity>,
    val managers: Map<String, String>,
) {
    fun complete(context: QueryContext): List<String> {
        if (context.kind == "manager") return managers.keys.sorted().map { "\\$it::class" }
        if (context.kind == "parameter") return context.parameters.distinct().sorted()
        val manager = managers.keys.firstOrNull { it.equals(context.manager?.trimStart('\\'), ignoreCase = true) }
            ?: return emptyList()
        val root = context.rootAlias ?: managers[manager]?.substringAfterLast('\\')?.replaceFirstChar { it.lowercase() }
            ?: return emptyList()
        val aliases = mutableMapOf(root to managers.getValue(manager))
        for (join in context.joins) {
            val parentEntity = aliases[join["fromAlias"]] ?: continue
            val nextManager = entities[parentEntity]?.relations?.get(join["relation"]) ?: continue
            val nextEntity = managers[nextManager] ?: continue
            val name = join["alias"] ?: join["relation"] ?: continue
            aliases[name] = nextEntity
        }
        return when (context.kind) {
            "alias" -> aliases.keys.sorted()
            "projection" -> aliases.flatMap { (alias, entity) ->
                entities[entity]?.fields.orEmpty().map { "$alias.$it" }
            }.distinct().sorted()
            "field", "relation" -> {
                val entity = aliases[context.alias ?: root] ?: return emptyList()
                val info = entities[entity] ?: return emptyList()
                if (context.kind == "field") info.fields.sorted() else info.relations.keys.sorted()
            }
            else -> emptyList()
        }
    }
}

/** Constrained PHP attributes parser: never evaluates PHP and rejects unknown mappings. */
internal object NativePhpIndexer {
    private const val ORM_ENTITY = "Small\\SwooleEntityManager\\Entity\\Attribute\\OrmEntity"
    private const val FIELD = "Small\\SwooleEntityManager\\Entity\\Attribute\\Field"
    private const val PRIMARY = "Small\\SwooleEntityManager\\Entity\\Attribute\\PrimaryKey"
    private const val TO_ONE = "Small\\SwooleEntityManager\\Entity\\Attribute\\ToOne"
    private const val TO_MANY = "Small\\SwooleEntityManager\\Entity\\Attribute\\ToMany"
    private const val MANAGER_ENTITY = "Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity"
    private val attributeBlock = Regex("""#\[(?:[^\[\]]|\[[^\]]*\])*\]""", RegexOption.DOT_MATCHES_ALL)
    private val attributeHead = Regex("""^#\[\s*([\\\w]+)""")
    private val classDeclaration = Regex(
        """\b(?:abstract\s+|final\s+|readonly\s+)*(class|trait)\s+(\w+)(?:\s+extends\s+([\\\w]+))?[^{;]*\{""",
    )
    private val propertyDeclaration = Regex(
        """((?:#\[(?:[^\[\]]|\[[^\]]*\])*\]\s*)+)(?:(?:public|private|protected|static|readonly)\s+)*(?:[\\\w?|]+\s+)?\$(\w+)""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val phpNamespace = Regex("""\bnamespace\s+([\\\w]+)\s*;""")
    private val phpUse = Regex("""(?m)^\s*use\s+([^;]+);""")
    private val classArgument = Regex("""([\\\w]+)\s*::\s*class\b""")

    private data class Symbol(
        val name: String,
        val parent: String?,
        val traits: List<String>,
        val entity: Boolean,
        val managedEntity: String?,
        val fields: Set<String>,
        val relations: Map<String, String?>,
    )

    fun index(files: Map<String, String>): NativeProjectIndex {
        val symbols = files.toSortedMap().flatMap { (_, source) ->
            if (source.length > 2 * 1024 * 1024) emptyList() else symbols(source)
        }.associateBy { it.name }
        val entities = mutableMapOf<String, NativeEntity>()
        val managers = mutableMapOf<String, String>()
        for (symbol in symbols.values) {
            if (symbol.entity) {
                val fields = linkedSetOf<String>()
                val relations = linkedMapOf<String, String?>()
                fun inherit(name: String, visited: MutableSet<String>) {
                    if (!visited.add(name)) return
                    val s = symbols[name] ?: return
                    (listOfNotNull(s.parent) + s.traits).forEach { inherit(it, visited) }
                    fields += s.fields
                    relations += s.relations
                }
                inherit(symbol.name, mutableSetOf())
                entities[symbol.name] = NativeEntity(fields, relations)
            }
            if (symbol.managedEntity != null) managers[symbol.name] = symbol.managedEntity
        }
        return NativeProjectIndex(entities.toSortedMap(), managers.toSortedMap())
    }

    private fun symbols(source: String): List<Symbol> {
        val result = mutableListOf<Symbol>()
        for (declaration in classDeclaration.findAll(source)) {
            val bodyStart = declaration.range.last
            val bodyEnd = findClosingBrace(source, bodyStart) ?: continue
            val before = source.substring(0, declaration.range.first)
            val imports = PhpNameResolver.imports(before)
            val namespace = phpNamespace.findAll(before).lastOrNull()?.groupValues?.get(1)
            fun resolve(raw: String): String = PhpNameResolver.resolve(raw, namespace, imports)
            val prefix = before.takeLast(8192)
            val trailing = Regex("""(?:#\[(?:[^\[\]]|\[[^\]]*\])*\]\s*)+$""", RegexOption.DOT_MATCHES_ALL)
                .find(prefix)?.value.orEmpty()
            val classAttrs = parseAttributes(trailing, ::resolve)
            val body = source.substring(bodyStart + 1, bodyEnd)
            val fields = linkedSetOf<String>()
            val relations = linkedMapOf<String, String?>()
            for (property in propertyDeclaration.findAll(body)) {
                val name = property.groupValues[2]
                for (attr in parseAttributes(property.groupValues[1], ::resolve)) {
                    when (attr.first) {
                        FIELD, PRIMARY -> fields += name
                        TO_ONE, TO_MANY -> relations[name] = classArgument.find(attr.second)
                            ?.groupValues?.get(1)?.let(::resolve)
                    }
                }
            }
            val traitUses = phpUse.findAll(body).filter { it.range.first < body.indexOf("function").let { p ->
                if (p < 0) body.length else p
            } }.flatMap { it.groupValues[1].split(',').asSequence() }
                .map { resolve(it.trim()) }.toList()
            val parent = declaration.groupValues[3].takeIf { it.isNotBlank() }?.let(::resolve)
            val managed = classAttrs.firstOrNull { it.first == MANAGER_ENTITY }?.second
                ?.let { classArgument.find(it)?.groupValues?.get(1)?.let(::resolve) }
            result += Symbol(
                name = listOfNotNull(namespace, declaration.groupValues[2]).joinToString("\\"),
                parent = parent,
                traits = traitUses,
                entity = classAttrs.any { it.first == ORM_ENTITY },
                managedEntity = managed,
                fields = fields,
                relations = relations,
            )
        }
        return result
    }

    private fun parseAttributes(text: String, resolve: (String) -> String): List<Pair<String, String>> =
        attributeBlock.findAll(text).mapNotNull { match ->
            val name = attributeHead.find(match.value)?.groupValues?.get(1) ?: return@mapNotNull null
            resolve(name) to match.value.substringAfter('(', "").substringBeforeLast(')', "")
        }.toList()

    private fun findClosingBrace(text: String, start: Int): Int? {
        var depth = 0
        var quote: Char? = null
        var escape = false
        var blockComment = false
        var lineComment = false
        for (i in start until text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            if (lineComment) { if (c == '\n') lineComment = false; continue }
            if (blockComment) { if (c == '*' && next == '/') blockComment = false; continue }
            if (quote != null) {
                if (escape) escape = false else if (c == '\\') escape = true else if (c == quote) quote = null
                continue
            }
            if (c == '/' && next == '*') { blockComment = true; continue }
            if (c == '/' && next == '/') { lineComment = true; continue }
            if (c == '\'' || c == '"') { quote = c; continue }
            if (c == '{') depth++
            if (c == '}' && --depth == 0) return i
        }
        return null
    }
}

/** Namespace and grouped imports resolution for both indexing and factory type inference. */
internal object PhpNameResolver {
    private val names = Regex("""(?m)^\s*use\s+([^;]+);""")
    private val namespace = Regex("""\bnamespace\s+([\\\w]+)\s*;""")
    fun imports(before: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (match in names.findAll(before)) {
            val raw = match.groupValues[1].trim()
            if (raw.contains('{')) {
                val prefix = raw.substringBefore('{').trim().trimEnd('\\')
                val group = raw.substringAfter('{').substringBefore('}')
                group.split(',').forEach { addImport(result, "$prefix\\" + it.trim()) }
            } else raw.split(',').forEach { addImport(result, it.trim()) }
        }
        return result
    }
    private fun addImport(result: MutableMap<String, String>, input: String) {
        val parts = input.split(Regex("""\s+as\s+""", RegexOption.IGNORE_CASE))
        val fqcn = parts.first().trimStart('\\')
        result[parts.getOrNull(1)?.trim() ?: fqcn.substringAfterLast('\\')] = fqcn
    }
    fun resolve(name: String, currentNamespace: String?, imports: Map<String, String>): String {
        val cleaned = name.trim().trimStart('\\')
        if (name.trim().startsWith("\\")) return cleaned
        val first = cleaned.substringBefore('\\')
        val imported = imports[first]
        if (imported != null) return imported + cleaned.removePrefix(first)
        return if (currentNamespace.isNullOrBlank()) cleaned else "$currentNamespace\\$cleaned"
    }
    fun fromSource(source: String, offset: Int, name: String): String? {
        if (source.length > 256 * 1024 || offset > source.length) return null
        val before = source.substring(0, offset)
        val currentNamespace = namespace.findAll(before).lastOrNull()?.groupValues?.get(1)
        return resolve(name, currentNamespace, imports(before))
    }
}

/** Scan on a background worker; vendor, generated folders and symlinks are never traversed. */
internal object NativeProjectScanner {
    private val excluded = setOf("vendor", ".git", ".idea", ".vscode", "node_modules", "var", "cache", "build", "dist")
    private const val MAX_FILES = 10_000
    private const val MAX_BYTES = 2L * 1024 * 1024

    fun scan(root: File): NativeProjectIndex {
        if (!root.isDirectory) return NativeProjectIndex(emptyMap(), emptyMap())
        val files = linkedMapOf<String, String>()
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root.toPath() && dir.fileName.toString() in excluded) FileVisitResult.SKIP_SUBTREE
                else FileVisitResult.CONTINUE

            override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (files.size >= MAX_FILES) return FileVisitResult.TERMINATE
                if (!attrs.isRegularFile || attrs.size() > MAX_BYTES || !path.fileName.toString().endsWith(".php", true)) {
                    return FileVisitResult.CONTINUE
                }
                runCatching {
                    files[root.toPath().relativize(path).toString()] = Files.readString(path, StandardCharsets.UTF_8)
                }
                return FileVisitResult.CONTINUE
            }
        })
        return NativePhpIndexer.index(files)
    }
}
