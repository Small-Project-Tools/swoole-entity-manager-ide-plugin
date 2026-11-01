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
}
