package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** All fixtures in this file are intentionally generic, not application code. */
class QueryContextRegressionTest {
    private val prefix = """<?php
        namespace Sample;
        use Sample\Manager\PersonManager;
        /** @var PersonManager ${'manager */
        @D@manager = @D@this->factory->get(managerClass: PersonManager::class);
    """.trimIndent()

    @Test fun namedFactoryAndFluentJoin() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'owner')->endJoin()
                ->where()->firstCondition(var1: @D@query->getFieldForCondition(fieldName: '
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedJoinRelationSuggestsRelationsFromItsSource() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsSurviveSeparateStatementsOnStoredBuilder() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@query = @D@manager->createQueryBuilder('person');
            @D@query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}manager */
        ${'manager = @D@this->factory->get(managerClass: PersonManager::class);
    """.trimIndent()

    @Test fun namedFactoryAndFluentJoin() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'owner')->endJoin()
                ->where()->firstCondition(var1: @D@query->getFieldForCondition(fieldName: '
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedJoinRelationSuggestsRelationsFromItsSource() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsSurviveSeparateStatementsOnStoredBuilder() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@query = @D@manager->createQueryBuilder('person');
            @D@query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}manager = ${'this->factory->get(managerClass: PersonManager::class);
    """.trimIndent()

    @Test fun namedFactoryAndFluentJoin() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'owner')->endJoin()
                ->where()->firstCondition(var1: @D@query->getFieldForCondition(fieldName: '
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedJoinRelationSuggestsRelationsFromItsSource() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsSurviveSeparateStatementsOnStoredBuilder() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@query = @D@manager->createQueryBuilder('person');
            @D@query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}this->factory->get(managerClass: PersonManager::class);
    """.trimIndent()

    @Test fun namedFactoryAndFluentJoin() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'owner')->endJoin()
                ->where()->firstCondition(var1: @D@query->getFieldForCondition(fieldName: '
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedJoinRelationSuggestsRelationsFromItsSource() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsSurviveSeparateStatementsOnStoredBuilder() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@query = @D@manager->createQueryBuilder('person');
            @D@query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'owner')->endJoin()
                ->where()->firstCondition(var1: ${'query->getFieldForCondition(fieldName: '
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedJoinRelationSuggestsRelationsFromItsSource() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsSurviveSeparateStatementsOnStoredBuilder() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@query = @D@manager->createQueryBuilder('person');
            @D@query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}query->getFieldForCondition(fieldName: '
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedJoinRelationSuggestsRelationsFromItsSource() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsSurviveSeparateStatementsOnStoredBuilder() {
        val context = QueryContextResolver.resolve(prefix + """
            @D@query = @D@manager->createQueryBuilder('person');
            @D@query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsSurviveSeparateStatementsOnStoredBuilder() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'query = @D@manager->createQueryBuilder('person');
            @D@query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}query = ${'manager->createQueryBuilder('person');
            @D@query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}manager->createQueryBuilder('person');
            ${'query->leftJoin('person', 'owner', 'owner')->endJoin();
            @D@query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}query->leftJoin('person', 'owner', 'owner')->endJoin();
            ${'query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            @D@query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjection() {
        val source = prefix + """
            ${'query = @D@manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}query = ${'manager->createQueryBuilder(alias: 'person');
            @D@query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}manager->createQueryBuilder(alias: 'person');
            ${'query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            @D@manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}query->addOrderBy(fieldName: 'created
        """.trimIndent()
        assertEquals("created", QueryContextResolver.resolve(source)?.typedPrefix)
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
        assertEquals("field", QueryContextResolver.methodArgumentRole("getFieldForCondition", "fieldName", 0))
    }

    @Test fun incorrectConditionSignatureIsNotTreatedAsField() {
        assertNull(QueryContextResolver.resolve(prefix + """
            ${'manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
}manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertNull(QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
        assertNull(QueryContextResolver.methodArgumentRole("getFieldForCondition", "unrelated", 0))
    }
}
