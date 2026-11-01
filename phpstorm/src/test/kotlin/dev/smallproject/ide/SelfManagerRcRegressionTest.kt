package dev.smallproject.ide

import java.io.File
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Public, synthetic PHP fixtures: no customer source or source-derived artifacts. */
class SelfManagerRcRegressionTest {
    private val index = NativeProjectScanner.scan(File("../tests/Functional/Fixtures/Project"))
    private val base = """
        <?php
        namespace Demo\EntityManager;
        class UserManager {
          public function run(): void {
    """.trimIndent() + "\n"

    private fun code(body: String) = base + body
    private val assigned = """
        ${'$'}query = ${'$'}this->createQueryBuilder('user');
    """.trimIndent() + "\n"

    @Test fun selfManagerBuildsFieldCompletions() {
        val context = assertNotNull(QueryContextResolver.resolve(code(
            assigned + "${'$'}query->where()->firstCondition(${'$'}query->getFieldForCondition(",
        )))
        assertEquals("field", context.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context.manager)
        assertEquals("user", context.rootAlias)
        assertEquals(listOf("companyId", "createdAt", "email", "id"), index.complete(context))
    }

    @Test fun selfManagerSupportsInlineQueryBuilders() {
        val context = assertNotNull(QueryContextResolver.resolve(code(
            "${'$'}this->createQueryBuilder('user')->where()->firstCondition(",
        )))
        assertEquals("condition-field", context.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context.manager)
        assertEquals("user", context.rootAlias)
        assertTrue(index.complete(context).any { it.contains("getFieldForCondition") })
    }

    @Test fun selfManagerPreservesJoinedFields() {
        val context = assertNotNull(QueryContextResolver.resolve(code(
            "${'$'}query = ${'$'}this->createQueryBuilder('user')\n" +
            " ->leftJoin('user', 'company', 'owner')->endJoin();\n" +
            "${'$'}query->getFieldForCondition('owner.n",
        )))
        assertEquals("field", context.kind)
        assertEquals("owner.n", context.typedPrefix)
        assertEquals(listOf("owner.name"), index.complete(context))
        assertEquals(1, context.joins.size)
    }

    @Test fun unrelatedClassDoesNotBecomeTheSelfManager() {
        val source = "<?php\nnamespace Demo;\nclass OrdinaryService {\n" +
            "${'$'}query = ${'$'}this->createQueryBuilder('user');\n" +
            "${'$'}query->getFieldForCondition("
        assertNull(QueryContextResolver.resolve(source))
    }

    @Test fun unknownManagerClassIsNotMappedToProjectEntities() {
        val source = "<?php\nnamespace Demo;\nclass FakeManager {\n" +
            "${'$'}query = ${'$'}this->createQueryBuilder('user');\n" +
            "${'$'}query->getFieldForCondition("
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertTrue(index.complete(context).isEmpty())
    }

    @TestFactory
    fun methodsAreRecognizedInsideTheCurrentManager(): List<DynamicTest> {
        val examples = listOf(
            "getFieldForCondition(" to "field",
            "addOrderBy(" to "field",
            "addFieldToUpdate(" to "field",
            "setParameter(" to "parameter",
            "select(" to "projection",
            "addSelect(" to "projection",
            "leftJoin(" to "alias",
            "innerJoin(" to "alias",
            "rightJoin(" to "alias",
            "join(" to "alias",
            "count(" to "projection",
            "sum(" to "projection",
            "avg(" to "projection",
            "min(" to "projection",
            "max(" to "projection",
        )
        val aliases = listOf("user", "record", "subject", "owner")
        return examples.flatMap { (method, kind) ->
            aliases.flatMap { alias ->
                listOf(true, false).map { stored ->
                    DynamicTest.dynamicTest("$method alias=$alias stored=$stored") {
                        val prefix = if (stored) {
                            "${'$'}query = ${'$'}this->createQueryBuilder('$alias');\n${'$'}query->$method"
                        } else {
                            "${'$'}this->createQueryBuilder('$alias')->$method"
                        }
                        val context = assertNotNull(QueryContextResolver.resolve(code(prefix)))
                        assertEquals(kind, context.kind)
                        if (kind != "parameter") {
                            assertEquals("Demo\\EntityManager\\UserManager", context.manager)
                            assertEquals(alias, context.rootAlias)
                        } else {
                            // Placeholder suggestions intentionally require no Manager lookup.
                            assertTrue(context.manager == null)
                        }
                    }
                }
            }
        }
    }

    @TestFactory
    fun conditionMethodsResolveWithBothRootConstructionForms(): List<DynamicTest> {
        val methods = listOf("firstCondition", "andCondition", "orCondition", "xorCondition")
        val expressions = listOf(
            "(" to "condition-field",
            "(var1: " to "condition-field",
        )
        return methods.flatMap { method ->
            expressions.flatMap { (suffix, kind) ->
                listOf(true, false).map { stored ->
                    DynamicTest.dynamicTest("$method$suffix stored=$stored") {
                        val b = if (stored) {
                            assigned + "${'$'}query->where()->$method$suffix"
                        } else "${'$'}this->createQueryBuilder('user')->where()->$method$suffix"
                        val context = assertNotNull(QueryContextResolver.resolve(code(b)))
                        assertEquals(kind, context.kind)
                        assertEquals("Demo\\EntityManager\\UserManager", context.manager)
                    }
                }
            }
        }
    }
}
