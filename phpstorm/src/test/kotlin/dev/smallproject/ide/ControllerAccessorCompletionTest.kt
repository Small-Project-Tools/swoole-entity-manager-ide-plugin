package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic PHP only: never include third-party project source or fixtures. */
class ControllerAccessorCompletionTest {
    private fun php(source: String): String = source.trimIndent().replace('@', 36.toChar())

    private val index = NativePhpIndexer.index(
        mapOf(
            "Item.php" to php("""
                <?php
                namespace Synthetic\Entity;
                use Small\SwooleEntityManager\Entity\Attribute\Field;
                class Item {
                    #[Field] private string @sku;
                    #[Field] private string @description;
                }
            """),
            "ItemManager.php" to """
                <?php
                namespace Synthetic\Manager;
                use Synthetic\Entity\Item;
                use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
                #[Entity(Item::class)]
                class ItemManager {}
            """.trimIndent(),
        ),
    )

    private val header = """
        <?php
        namespace Synthetic\Http;
        use Synthetic\Manager\ItemManager;
        final class ItemsController {
            public function handle(): void {
    """.trimIndent() + "\n"

    @Test fun chainedAccessorResolvesNestedFieldCompletion() {
        val source = header + php("""
            @query = @this->itemManager()->createQueryBuilder(alias: 'item');
            @query->where()->firstCondition(@query->getFieldForCondition(
        """)
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertEquals("field", context.kind)
        assertEquals(index.managers.keys.single(), context.manager)
        assertEquals("item", context.rootAlias)
        assertEquals(php("@query"), context.queryReference)
        assertTrue("sku" in index.complete(context))
        assertTrue("description" in index.complete(context))
        assertEquals("field", SmallCompletionAutoPopup.contextForTypedText(source, '(')?.kind)
    }

    @Test fun conditionFieldResolvesFromChainedManagerMethod() {
        val context = assertNotNull(QueryContextResolver.resolve(header + php("""
            @query = @this->itemManager()->createQueryBuilder('item');
            @query->where()->firstCondition(
        """)))
        assertEquals("condition-field", context.kind)
        assertTrue(index.complete(context).contains(php("@query->getFieldForCondition('sku')")))
    }

    @Test fun inlineBuilderFromAccessorProposesValidExpressions() {
        val context = assertNotNull(QueryContextResolver.resolve(header + php("""
            @this->itemManager()->createQueryBuilder('item')->where()->firstCondition(
        """)))
        assertEquals("condition-field", context.kind)
        assertEquals(php("@this->itemManager()->createQueryBuilder('item')"), context.queryReference)
        assertTrue(index.complete(context).contains(
            php("@this->itemManager()->createQueryBuilder('item')->getFieldForCondition('sku')"),
        ))
    }

    @Test fun accessorWorksWithoutImportWhenMappedManagerIsUnique() {
        val context = assertNotNull(QueryContextResolver.resolve(php("""
            <?php
            namespace Synthetic\Http;
            class ItemsController {
                function handle() {
                    @query = @this->itemManager()->createQueryBuilder('item');
                    @query->getFieldForCondition(
        """)))
        assertEquals("ItemManager", context.manager)
        assertTrue("sku" in index.complete(context))
        val ambiguous = index.copy(
            managers = index.managers + ("Other\\ItemManager" to "Other\\Item"),
        )
        assertTrue(ambiguous.complete(context).isEmpty())
    }

    @Test fun getPrefixedAccessorResolvesManager() {
        val context = assertNotNull(QueryContextResolver.resolve(header + php("""
            @query = @this->getItemManager()->createQueryBuilder('item');
            @query->getFieldForCondition(
        """)))
        assertTrue("sku" in index.complete(context))
    }

    @Test fun unrelatedAndForeignAccessorsAreNotClaimed() {
        assertNull(QueryContextResolver.resolve(header + php("""
            @query = @this->cache()->createQueryBuilder('item');
            @query->getFieldForCondition(
        """)))
        assertNull(QueryContextResolver.resolve(header + php("""
            @query = @service->itemManager()->createQueryBuilder('item');
            @query->getFieldForCondition(
        """)))
        val unmapped = assertNotNull(QueryContextResolver.resolve(header + php("""
            @query = @this->cacheManager()->createQueryBuilder('item');
            @query->getFieldForCondition(
        """)))
        assertTrue(index.complete(unmapped).isEmpty())
    }

    @Test fun accessorsDoNotLeakBuilderIntoAnotherMethod() {
        assertNull(QueryContextResolver.resolve(header + php("""
            @query = @this->itemManager()->createQueryBuilder('item');
            }
            public function other(): void {
                @query->getFieldForCondition(
        """)))
    }
}
