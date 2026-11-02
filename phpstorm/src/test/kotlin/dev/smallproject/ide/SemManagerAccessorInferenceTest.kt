package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Synthetic PHP fixtures only: no customer source or runtime dependency. */
class SemManagerAccessorInferenceTest {
    private val expected = "\\Synthetic\\Manager\\CatalogManager"

    private fun inferred(body: String, accessor: String = "catalogManager"): String? {
        val source = """
            <?php
            namespace Synthetic\Controller;
            use Synthetic\Manager\CatalogManager as Catalog;
            use Synthetic\Manager\OrderManager;
            use Small\SwooleEntityManagerBundle\Contract\EntityManagerFactoryInterface as Locator;
            class Controller {
                public function __construct(private readonly Locator ${'$'}factory) {}
                $body
                public function run() { ${'$'}this->$accessor(); }
            }
        """.trimIndent()
        return SemManagerAccessorInference.infer(
            source, source.lastIndexOf("\$this->$accessor()"), accessor)
    }

    @Test fun directAndNamedFactoryReturns() {
        for (arg in listOf("Catalog::class", "manager: Catalog::class",
            "managerClass: Catalog::class", "new: true, managerClass: Catalog::class")) {
            assertEquals(expected, inferred("""
                private function catalogManager(): \Small\SwooleEntityManager\EntityManager\AbstractManager {
                    return ${'$'}this->factory->get($arg);
                }
            """.trimIndent()))
        }
    }

    @Test fun assignmentAndDelegationReturnConcreteManager() {
        assertEquals(expected, inferred("""
            private function catalogManager() {
                ${'$'}result = ${'$'}this->factory->get(Catalog::class);
                return ${'$'}result;
            }
        """.trimIndent()))
        assertEquals(expected, inferred("""
            private function catalogManager() { return ${'$'}this->delegateManager(); }
            private function delegateManager() { return ${'$'}this->factory->get(Catalog::class); }
        """.trimIndent()))
    }

    @Test fun matchingBranchesAreSafeButMixedOrNullAreNot() {
        assertEquals(expected, inferred("""
            private function catalogManager() {
                if (${'$'}condition) return ${'$'}this->factory->get(Catalog::class);
                return ${'$'}this->factory->get(Catalog::class);
            }
        """.trimIndent()))
        assertNull(inferred("""
            private function catalogManager() {
                if (${'$'}condition) return ${'$'}this->factory->get(OrderManager::class);
                return ${'$'}this->factory->get(Catalog::class);
            }
        """.trimIndent()))
        assertNull(inferred("""
            private function catalogManager(): ?object {
                if (${'$'}condition) return null;
                return ${'$'}this->factory->get(Catalog::class);
            }
        """.trimIndent()))
    }

    @Test fun foreignFactoryAndReassignedVariableFailClosed() {
        assertNull(inferred("""
            private function catalogManager() { return ${'$'}this->cache->get(Catalog::class); }
        """.trimIndent()))
        assertNull(inferred("""
            private function catalogManager() {
                ${'$'}result = ${'$'}this->factory->get(Catalog::class);
                ${'$'}result = null;
                return ${'$'}result;
            }
        """.trimIndent()))
    }

    @Test fun recursionCommentsAndMissingMethodsFailClosed() {
        assertNull(inferred("""
            private function catalogManager() { return ${'$'}this->delegateManager(); }
            private function delegateManager() { return ${'$'}this->catalogManager(); }
        """.trimIndent()))
        assertNull(inferred("""
            /* private function catalogManager() { return ${'$'}this->factory->get(Catalog::class); } */
            private function unrelated() { return null; }
        """.trimIndent()))
        assertNull(inferred("private function unrelated() { return null; }"))
    }

    @Test fun anotherClassCannotProvideTheAccessor() {
        val source = """
            <?php
            namespace Synthetic;
            use Synthetic\Manager\CatalogManager;
            class First { function catalogManager() { return ${'$'}factory->get(CatalogManager::class); } }
            class Second {
                function catalogManager() { return null; }
                function run() { ${'$'}this->catalogManager(); }
            }
        """.trimIndent()
        assertNull(SemManagerAccessorInference.infer(
            source, source.lastIndexOf("\$this->catalogManager()"), "catalogManager"))
    }

    @Test fun invalidInputsFailClosed() {
        assertNull(SemManagerAccessorInference.infer("<?php", 0, "catalogManager"))
        assertNull(SemManagerAccessorInference.infer("<?php", -1, "catalogManager"))
        val huge = "<?php\n" + "x".repeat(QueryContextResolver.MAX_SOURCE_LENGTH + 1)
        assertNull(SemManagerAccessorInference.infer(huge, huge.length, "catalogManager"))
    }
}
