package dev.smallproject.ide

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Uses the same public generic PHP fixtures as the shared core tests. */
class NativePhpIndexerTest {
    private val root = File("../tests/Functional/Fixtures/Project")

    @Test fun indexesFieldsRelationsAndTraitsWithoutPhpRuntime() {
        val index = NativeProjectScanner.scan(root)
        val entity = index.entities.getValue("Demo\\Entity\\User")
        assertEquals(setOf("id", "email", "companyId", "createdAt"), entity.fields)
        assertEquals("Demo\\EntityManager\\CompanyManager", entity.relations["company"])
        assertEquals("Demo\\Entity\\User", index.managers["Demo\\EntityManager\\UserManager"])
    }

    @Test fun completesFieldsAliasesJoinsAndParameters() {
        val index = NativeProjectScanner.scan(root)
        val manager = "Demo\\EntityManager\\UserManager"
        val context = QueryContext("field", manager, "user")
        assertEquals(listOf("companyId", "createdAt", "email", "id"), index.complete(context))
        val joined = listOf(mapOf("fromAlias" to "user", "relation" to "company", "alias" to "owner"))
        assertEquals(listOf("company"), index.complete(context.copy(kind = "relation")))
        assertEquals(listOf("name", "id").sorted(), index.complete(context.copy(alias = "owner", joins = joined)))
        assertEquals(listOf("owner", "user"), index.complete(context.copy(kind = "alias", joins = joined)).sorted())
        assertTrue(index.complete(context.copy(kind = "projection", joins = joined)).contains("owner.name"))
        assertEquals(listOf("a", "z"), index.complete(context.copy(kind = "parameter", parameters = listOf("z", "a", "a"))))
    }

    @Test fun completesFactoryClassReferences() {
        val index = NativeProjectScanner.scan(root)
        assertTrue(index.complete(QueryContext("manager")).contains("\\Demo\\EntityManager\\UserManager::class"))
        assertFalse(index.complete(QueryContext("manager")).any { it.contains("Demo\\Entity\\User::class") })
    }

    @Test fun missesUnknownRelationshipsRatherThanSuggestingWrongFields() {
        val index = NativeProjectScanner.scan(root)
        val invalid = QueryContext("field", "Demo\\EntityManager\\UserManager", "u",
            alias = "bad", joins = listOf(mapOf("fromAlias" to "u", "relation" to "unknown", "alias" to "bad")))
        assertTrue(index.complete(invalid).isEmpty())
    }

    @Test fun qualifiedFieldLookupOffersValidAliasLabels() {
        val index = NativeProjectScanner.scan(root)
        val context = QueryContext(
            "field", "Demo\\EntityManager\\UserManager", "user",
            joins = listOf(mapOf("fromAlias" to "user", "relation" to "company", "alias" to "owner")),
            typedPrefix = "owner.n",
        )
        assertEquals(listOf("owner.name"), index.complete(context))
        assertEquals(listOf("user.email"), index.complete(context.copy(typedPrefix = "user.em")))
    }

    @Test fun managerMappingIsAuthoritativeEvenWithoutAnEntityMarker() {
        val files = mapOf(
            "Legacy.php" to """
                <?php
                namespace Generic;
                use Small\SwooleEntityManager\Entity\Attribute\Field;
                class Legacy { #[Field] private string ${'$'}name; }
            """.trimIndent().replace("Small\\\\", "Small\\"),
            "LegacyManager.php" to """
                <?php
                namespace Generic;
                use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
                #[Entity(Legacy::class)]
                class LegacyManager {}
            """.trimIndent().replace("Small\\\\", "Small\\"),
        )
        val index = NativePhpIndexer.index(files)
        assertEquals(setOf("name"), index.entities.getValue("Generic\\Legacy").fields)
        assertEquals("Generic\\Legacy", index.managers["Generic\\LegacyManager"])
    }

}
