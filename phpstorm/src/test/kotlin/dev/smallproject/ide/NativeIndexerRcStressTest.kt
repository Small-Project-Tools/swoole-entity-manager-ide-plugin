package dev.smallproject.ide

import java.nio.file.Files
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Generated source only; never includes customer project code. */
class NativeIndexerRcStressTest {
    private val managerCount = 240
    private val sources: Map<String, String> by lazy {
        buildMap {
            for (i in 0 until managerCount) {
                val next = (i + 1) % managerCount
                val managerType = "Node" + i + "Manager"
                val nextManagerType = "Node" + next + "Manager"
                put("entities/Node$i.php", """
                    <?php
                    namespace RcFixtures\Entity;
                    use Small\SwooleEntityManager\Entity\Attribute\{Field, ToOne};
                    class Node$i {
                        #[Field] private string ${'$'}code;
                        #[Field] private ?int ${'$'}sequence = null;
                        #[ToOne(\RcFixtures\EntityManager\$nextManagerType::class)]
                        private ?Node$next ${'$'}next = null;
                    }
                """.trimIndent())
                put("managers/$managerType.php", """
                    <?php
                    namespace RcFixtures\EntityManager;
                    use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
                    #[Entity(\RcFixtures\Entity\Node$i::class)]
                    class $managerType {}
                """.trimIndent())
            }
        }
    }
    private val index: NativeProjectIndex by lazy {
        NativePhpIndexer.index(sources)
    }

    @Test fun largeGraphOfManagersAndEntitiesIsMappedCompletely() {
        assertEquals(managerCount, index.managers.size)
        assertEquals(managerCount, index.entities.size)
        assertTrue(index.entities.values.all { it.fields == setOf("code", "sequence") })
        assertEquals(managerCount, index.entities.values.sumOf { it.relations.size })
    }

    @Test fun cyclicRelationsAreFiniteAndSelectable() {
        val joins = listOf(
            mapOf("fromAlias" to "n0", "relation" to "next", "alias" to "n1"),
            mapOf("fromAlias" to "n1", "relation" to "next", "alias" to "n2"),
            mapOf("fromAlias" to "n2", "relation" to "next", "alias" to "n3"),
        )
        val query = QueryContext(
            kind = "field", manager = "RcFixtures\\EntityManager\\Node0Manager",
            rootAlias = "n0", typedPrefix = "n3.co", joins = joins,
        )
        assertEquals(listOf("n3.code"), index.complete(query))
        assertEquals(listOf("n0", "n1", "n2", "n3"),
            index.complete(query.copy(kind = "alias")).sorted())
    }

    @TestFactory fun eachManagerSupportsFieldsAndConditionExpressions(): List<DynamicTest> {
        return (0 until managerCount).map { i ->
            DynamicTest.dynamicTest("manager #$i") {
                val managerType = "Node" + i + "Manager"
                val context = QueryContext("field", "RcFixtures\\EntityManager\\$managerType", "n")
                assertEquals(listOf("code", "sequence"), index.complete(context))
                val all = index.complete(context.copy(kind = "condition-field", queryReference = "${'$'}query"))
                assertEquals(2, all.size)
                assertTrue(all.all { it.startsWith("${'$'}query->getFieldForCondition('") })
            }
        }
    }

    @Test fun malformedPhpDoesNotBreakHealthySymbols() {
        val files = sources.toMutableMap()
        files["invalid.php"] = "<?php // incomplete\nclass Broken { #[Field(" +
            " { ".repeat(45) + "\"'  ; /** }"
        val parsed = NativePhpIndexer.index(files)
        assertEquals(managerCount, parsed.managers.size)
        assertEquals(managerCount, parsed.entities.size)
    }

    @Test fun scannerSkipsVendorCacheSymlinksAndLargeFiles() {
        val root = Files.createTempDirectory("small-ide-rc-fixtures")
        try {
            val sources = root.resolve("src")
            val vendor = root.resolve("vendor")
            val cache = root.resolve("cache")
            Files.createDirectories(sources)
            Files.createDirectories(vendor)
            Files.createDirectories(cache)
            val entity = """
                <?php
                namespace Synthetic;
                use Small\SwooleEntityManager\Entity\Attribute\Field;
                class Included { #[Field] private int ${'$'}id; }
            """.trimIndent()
            val manager = """
                <?php
                namespace Synthetic;
                use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
                #[Entity(Included::class)] class IncludedManager {}
            """.trimIndent()
            Files.writeString(sources.resolve("Included.php"), entity)
            Files.writeString(sources.resolve("IncludedManager.php"), manager)
            Files.writeString(vendor.resolve("Ignored.php"), entity.replace("Included", "VendorOnly"))
            Files.writeString(cache.resolve("IgnoredManager.php"), manager.replace("Included", "CachedOnly"))
            Files.writeString(sources.resolve("Oversized.php"), " ".repeat(2 * 1024 * 1024 + 16))
            runCatching { Files.createSymbolicLink(sources.resolve("linkedVendor.php"), vendor.resolve("Ignored.php")) }
            val actual = NativeProjectScanner.scan(root.toFile())
            assertEquals(mapOf("Synthetic\\IncludedManager" to "Synthetic\\Included"), actual.managers)
            assertEquals(setOf("id"), actual.entities.getValue("Synthetic\\Included").fields)
            assertEquals(1, actual.entities.size)
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun cancellationDuringFilesystemTraversal() {
        val root = Files.createTempDirectory("small-ide-rc-cancel")
        try {
            repeat(50) { i ->
                Files.writeString(root.resolve("file$i.php"), "<?php class Placeholder$i {}")
            }
            var count = 0
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                NativeProjectScanner.scan(root.toFile()) {
                    if (++count >= 5) throw IllegalArgumentException("cancelled")
                }
            }
            assertTrue(count >= 5)
        } finally { root.toFile().deleteRecursively() }
    }
}
