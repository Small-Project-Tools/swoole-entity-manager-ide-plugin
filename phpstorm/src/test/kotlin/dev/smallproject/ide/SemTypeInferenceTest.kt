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

    @Test fun matrixOfCoreAndBundleFactoryReturns() {
        val factories = listOf(
            "Small\\SwooleEntityManager\\Factory\\EntityManagerFactory",
            "Small\\SwooleEntityManagerBundle\\Factory\\EntityManagerFactory",
            "Small\\SwooleEntityManagerBundle\\Contract\\EntityManagerFactoryInterface",
        )
        val managers = listOf("UserManager", "CatalogManager", "OrderManager", "InventoryManager")
        for (factory in factories) for (manager in managers) {
            val source = "<?php\nuse Synthetic\\Manager\\$manager;\n" +
                "/** @var \\$factory \$locator */\n"
            for (argument in listOf(
                "$manager::class",
                "manager: $manager::class",
                "managerClass: $manager::class",
            )) {
                assertEquals(
                    "\\Synthetic\\Manager\\$manager",
                    SemTypeInference.factoryResult(source, source.length, argument, "\$locator"),
                )
            }
        }
    }

    @Test fun ambiguousFactoryArgumentsAreNotTyped() {
        assertNull(SemTypeInference.managerArgument(listOf(
            "UserManager::class", "managerClass: OrderManager::class",
        )))
        assertNull(SemTypeInference.managerArgument(listOf("new: true")))
        assertNull(SemTypeInference.managerArgument(listOf(
            "managerClass: UserManager::class", "manager: OrderManager::class",
        )))
    }

    @Test fun namedParameterTextRetainsNamesAndNestedExpressions() {
        assertEquals("managerClass: UserManager::class",
            SemTypeInference.managerArgumentFromParameterList(
                "(new: helper('hello, world', [1, 2]), managerClass: UserManager::class)"
            ))
        assertEquals("manager: UserManager::class",
            SemTypeInference.managerArgumentFromParameterList(
                "(manager: UserManager::class, new: false)"
            ))
        assertEquals("UserManager::class",
            SemTypeInference.managerArgumentFromParameterList("(UserManager::class)"))
        assertEquals("UserManager::class",
            SemTypeInference.managerArgumentFromParameterList("UserManager::class"))
        assertEquals("managerClass: UserManager::class",
            SemTypeInference.managerArgumentFromParameterList(
                "(\nnew: true,\nmanagerClass: UserManager::class,\n)"
            ))
    }

    @Test fun malformedOrAmbiguousFactoryArgumentListsFailClosed() {
        listOf("", "(", "(managerClass: UserManager::class",
            "(managerClass: UserManager::class, new: [1, 2)",
            "(new: true, managerClass: UserManager::class, manager: AnotherManager::class)",
            "(managerClass: \"UserManager\")", "(new: true)",
            "(UserManager::class, managerClass: AnotherManager::class)"
        ).forEach { value ->
            assertNull(SemTypeInference.managerArgumentFromParameterList(value), value)
        }
    }

}
