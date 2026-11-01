package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Only generic PHP fixtures are used in this plugin project. */
class QueryContextRegressionTest {
    private val prefix = """<?php
        namespace Sample;
        use Sample\Manager\PersonManager;
        /** @var PersonManager ${'$'}manager */
        ${'$'}manager = ${'$'}this->factory->get(managerClass: PersonManager::class);
    """.trimIndent()

    @Test fun namedFactoryAndFluentJoin() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'owner')->endJoin()
                ->where()->firstCondition(var1: ${'$'}query->getFieldForCondition(fieldName: '
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedJoinRelation() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsInSeparateStatements() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person');
            ${'$'}query->leftJoin('person', 'owner', 'owner')->endJoin();
            ${'$'}query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjectionSignature() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder(alias: 'person');
            ${'$'}query->addOrderBy(fieldName: 'created
        """.trimIndent())
        assertEquals("created", context?.typedPrefix)
        assertEquals("field", context?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
    }

    @Test fun invalidConditionDoesNotClaimFieldCompletion() {
        assertNull(QueryContextResolver.resolve(prefix + """
            ${'$'}manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertEquals("condition-field", QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
    }

    @Test fun fluentJoinsMadeDuringBuilderAssignmentAreAvailableLater() {
        val source = """
            <?php
            namespace Sample;
            use Demo\EntityManager\UserManager;
            ${'$'}manager = ${'$'}factory->get(UserManager::class);
            ${'$'}query = ${'$'}manager->createQueryBuilder(alias: 'user')
                ->leftJoin(fromAlias: 'user', relationAlias: 'company', alias: 'owner')->endJoin();
            ${'$'}query->where()->firstCondition(${'$'}query->getFieldForCondition('
        """.trimIndent()
        val context = QueryContextResolver.resolve(source)
        assertEquals("field", context?.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context?.manager)
        assertEquals("user", context?.rootAlias)
        assertEquals("company", context?.joins?.single()?.get("relation"))
        assertEquals("owner", context?.joins?.single()?.get("alias"))
    }


    @Test fun emptyUnquotedFieldArgumentInsideConditionIsCompletable() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person')
                ->leftJoin('person', 'owner', 'owner')->endJoin();
            ${'$'}query->where()->firstCondition(${'$'}query->getFieldForCondition(
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("alias"))
        assertEquals("", context?.typedPrefix)
    }

    @Test fun positionalFieldCompletionWorksBeforeTypingQuotes() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person');
            ${'$'}query->getFieldForCondition(
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("", context?.typedPrefix)
    }

    @Test fun namedAndPartiallyTypedUnquotedFieldCompletes() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person');
            ${'$'}query->getFieldForCondition(fieldName: deleted
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("deleted", context?.typedPrefix)
    }

    @Test fun dynamicUnquotedPhpExpressionsRemainUnclaimed() {
        assertNull(QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person');
            ${'$'}query->getFieldForCondition(${'$'}dynamic
        """.trimIndent()))
    }

    @Test fun groupedAliasedManagerImportResolvesToIndexedManager() {
        val source = listOf(
            "<?php",
            "namespace App;",
            "use Demo\\EntityManager\\{UserManager as AccountManager, CompanyManager};",
            "\$manager = \$factory->get(AccountManager::class);",
            "\$query = \$manager->createQueryBuilder('user');",
            "\$query->getFieldForCondition(",
        ).joinToString("\n")
        val context = QueryContextResolver.resolve(source)
        assertEquals("field", context?.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context?.manager)
        assertEquals("user", context?.rootAlias)
    }
}
