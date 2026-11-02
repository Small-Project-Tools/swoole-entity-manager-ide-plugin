package dev.smallproject.ide

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SemTypeInferenceTest {
    @Test fun returnsImportedManagerFromClassString() {
        val source = "<?php\nnamespace App;\nuse Demo\\EntityManager\\UserManager as MemberManager;\n"
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "managerClass: MemberManager::class", "$" + "factory"))
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "MemberManager::class", "$" + "factory"))
    }

    @Test fun returnsQualifiedManager() {
        val source = "<?php\nnamespace App;\n"
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "\\Demo\\EntityManager\\UserManager::class", "$" + "factory"))
        assertNull(SemTypeInference.factoryResult(source, source.length, "Unrelated::class", "$" + "factory"))
    }

    @Test fun doesNotInventTypesForUnrelatedGetMethods() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n"
        assertNull(SemTypeInference.factoryResult(
            source, source.length, "UserManager::class", "$" + "cache",
        ))
        assertNull(SemTypeInference.factoryResult(
            source, source.length, "UserManager::class", "$" + "repository",
        ))
    }

    @Test fun acceptsTypedSmallFactoryWithArbitraryVariableName() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n" +
            "/** @var \\Small\\SwooleEntityManager\\Factory\\EntityManagerFactory $" + "locator */\n"
        assertEquals("\\Demo\\EntityManager\\UserManager", SemTypeInference.factoryResult(
            source, source.length, "managerClass: UserManager::class", "$" + "locator",
        ))
    }

    @Test fun resolvesClassNamesWithGroupedImports() {
        val source = "<?php\nnamespace Current;\nuse Demo\\EntityManager\\{UserManager, CompanyManager as BillingManager};\n"
        assertEquals("Demo\\EntityManager\\BillingManager".replace("BillingManager", "CompanyManager"),
            PhpNameResolver.fromSource(source, source.length, "BillingManager"))
    }
    @Test fun acceptsManagerAliasWithoutManagerSuffix() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager as Account;\n"
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "Account::class", "$" + "factory"))
    }

    @Test fun acceptsTypedFactoryInterfaceWithArbitraryVariableName() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n" +
            "/** @var \\Small\\SwooleEntityManagerBundle\\Contract\\EntityManagerFactoryInterface " + "$" + "locator */\n"
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "UserManager::class", "$" + "locator"))
    }

    @Test fun unrelatedFactoryDoesNotHijackGetCall() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n"
        assertNull(SemTypeInference.factoryResult(source, source.length, "UserManager::class", "$" + "cacheFactory"))
    }
    @Test fun acceptsFactoryInterfaceImportedUnderAlias() {
        val source = "<?php\n" +
            "use Demo\\EntityManager\\UserManager;\n" +
            "use Small\\SwooleEntityManagerBundle\\Contract\\EntityManagerFactoryInterface as Locator;\n" +
            "function make(Locator \$service): void {\n"
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, "UserManager::class", "\$service"))
    }

    @Test fun rejectsUnrelatedFactoryWithSimilarTypeName() {
        val source = "<?php\n" +
            "use Demo\\EntityManager\\UserManager;\n" +
            "use Acme\\Other\\EntityManagerFactory;\n" +
            "function make(EntityManagerFactory \$service): void {\n"
        assertNull(SemTypeInference.factoryResult(source, source.length, "UserManager::class", "\$service"))
    }


    @Test fun reorderedNamedArgumentsResolveConcreteManager() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n"
        val argument = SemTypeInference.managerArgument(
            listOf("new: true", "managerClass: UserManager::class"))
        assertEquals("managerClass: UserManager::class", argument)
        assertEquals("\\Demo\\EntityManager\\UserManager",
            SemTypeInference.factoryResult(source, source.length, argument!!, "\$factory"))
        assertNull(SemTypeInference.managerArgument(listOf("new: true")))
    }

    @Test fun declaredForeignFactoryCannotInferSmallManager() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n" +
            "use Foreign\\OtherFactory;\nfunction test(OtherFactory \$factory): void {}\n"
        assertNull(SemTypeInference.factoryResult(
            source, source.length, "UserManager::class", "\$factory"))
    }
}
