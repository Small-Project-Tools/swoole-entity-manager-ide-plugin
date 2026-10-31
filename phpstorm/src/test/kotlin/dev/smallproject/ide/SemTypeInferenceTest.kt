package dev.smallproject.ide

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SemTypeInferenceTest {
    @Test fun returnsImportedManagerFromClassString() {
        val source = "<?php\nnamespace App;\nuse Demo\\EntityManager\\UserManager as MemberManager;\n"
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "managerClass: MemberManager::class"))
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "MemberManager::class"))
    }

    @Test fun returnsQualifiedManager() {
        val source = "<?php\nnamespace App;\n"
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "\\Demo\\EntityManager\\UserManager::class"))
        assertNull(SemTypeInference.factoryResult(source, source.length, "Unrelated::class"))
    }

    @Test fun returnsBuilderAndConditionTypesOnlyAfterFactoryUsage() {
        val source = "<?php\n" + "\$manager = \$factory->get(UserManager::class);\n" +
            "\$builder = \$manager->createQueryBuilder('user');\n"
        assertEquals("\\Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\QueryBuilder",
            SemTypeInference.builderResult("createQueryBuilder", source, source.length))
        assertEquals("\\Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\Bean\\Bracket",
            SemTypeInference.builderResult("where", source, source.length))
        assertNull(SemTypeInference.builderResult("createQueryBuilder", "<?php\n", 6))
        assertNull(SemTypeInference.builderResult("notSmall", source, source.length))
    }

    @Test fun resolvesClassNamesWithGroupedImports() {
        val source = "<?php\nnamespace Current;\nuse Demo\\EntityManager\\{UserManager, CompanyManager as BillingManager};\n"
        assertEquals("Demo\\EntityManager\\BillingManager".replace("BillingManager", "CompanyManager"),
            PhpNameResolver.fromSource(source, source.length, "BillingManager"))
    }
}
