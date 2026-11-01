package dev.smallproject.ide

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the real Core 3 firstCondition() argument types. */
class ConditionCompletionRegressionTest {
    private val index = NativeProjectScanner.scan(File("../tests/Functional/Fixtures/Project"))
    private val prefix = "<?php\n" +
        "use Demo\\EntityManager\\UserManager;\n" +
        "use Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\Enum\\ConditionOperatorType;\n" +
        "\$manager = \$factory->get(UserManager::class);\n" +
        "\$query = \$manager->createQueryBuilder('user');\n"

    @Test fun firstConditionCompletesConditionFieldsNotConstantStrings() {
        val context = assertNotNull(QueryContextResolver.resolve(
            prefix + "\$query->where()->firstCondition(",
        ))
        assertEquals("condition-field", context.kind)
        assertEquals("\$query", context.queryReference)
        assertEquals("Demo\\EntityManager\\UserManager", context.manager)
        assertEquals("user", context.rootAlias)
        val suggestions = index.complete(context)
        assertTrue("\$query->getFieldForCondition('email')" in suggestions)
        assertTrue("\$query->getFieldForCondition('id')" in suggestions)
        assertFalse("'email'" in suggestions)
        assertFalse("email" in suggestions)
    }

    @Test fun fieldCompletionSupportsNamedArguments() {
        val context = assertNotNull(QueryContextResolver.resolve(
            prefix + "\$query->where()->firstCondition(var1: ",
        ))
        assertEquals("condition-field", context.kind)
        assertTrue(index.complete(context).contains("\$query->getFieldForCondition('companyId')"))
    }

    @Test fun otherConditionMethodsAlsoCompleteFields() {
        for (method in listOf("andCondition", "orCondition", "xorCondition")) {
            val context = assertNotNull(QueryContextResolver.resolve(
                prefix + "\$query->where()->$method(",
            ))
            assertEquals("condition-field", context.kind)
            assertTrue(index.complete(context).contains("\$query->getFieldForCondition('email')"))
        }
    }

    @Test fun enumCompletionUsesActualCore3Names() {
        val context = assertNotNull(QueryContextResolver.resolve(
            prefix + "\$query->where()->firstCondition(\$query->getFieldForCondition('id'), ",
        ))
        assertEquals("condition-operator", context.kind)
        assertEquals("ConditionOperatorType", context.operatorReference)
        val suggestions = index.complete(context)
        assertEquals(15, suggestions.size)
        assertTrue("ConditionOperatorType::equal" in suggestions)
        assertTrue("ConditionOperatorType::isNull" in suggestions)
        assertTrue("ConditionOperatorType::notIn" in suggestions)
    }

    @Test fun enumCompletionSupportsTypedPrefixAndNamedArgument() {
        val context = assertNotNull(QueryContextResolver.resolve(
            prefix + "\$query->where()->firstCondition(operator: Con",
        ))
        assertEquals("condition-operator", context.kind)
        assertEquals("Con", context.typedPrefix)
        assertTrue(index.complete(context).contains("ConditionOperatorType::notEqual"))
    }

    @Test fun enumCompletionWithoutImportUsesFullyQualifiedClass() {
        val source = prefix.replace(
            "use Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\Enum\\ConditionOperatorType;\n",
            "",
        )
        val context = assertNotNull(QueryContextResolver.resolve(
            source + "\$query->where()->firstCondition(\$query->getFieldForCondition('email'), ",
        ))
        assertEquals(
            "\\Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\Enum\\ConditionOperatorType",
            context.operatorReference,
        )
        assertTrue(index.complete(context).first().startsWith("\\Small\\SwooleEntityManager\\"))
    }

    @Test fun conditionFieldsIncludeMappedRelationsAfterJoin() {
        val context = assertNotNull(QueryContextResolver.resolve(
            prefix + "\$query->leftJoin('user', 'company', 'owner')->endJoin();\n" +
                "\$query->where()->firstCondition(",
        ))
        val suggestions = index.complete(context)
        assertTrue("\$query->getFieldForCondition('name', 'owner')" in suggestions)
        assertTrue("\$query->getFieldForCondition('email')" in suggestions)
    }

    @Test fun conditionFieldsWorkForInlineBuilderWithoutQueryVariable() {
        val context = assertNotNull(QueryContextResolver.resolve(
            prefix + "\$manager->createQueryBuilder('user')->where()->firstCondition(",
        ))
        assertEquals("\$manager->createQueryBuilder('user')", context.queryReference)
        assertTrue(index.complete(context).contains(
            "\$manager->createQueryBuilder('user')->getFieldForCondition('email')",
        ))
    }

    @Test fun contextWorksWithManagerTypedParameter() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n" +
            "function build(UserManager \$manager): void {\n" +
            "  \$query = \$manager->createQueryBuilder('user');\n" +
            "  \$query->where()->firstCondition("
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertEquals("condition-field", context.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context.manager)
        assertTrue(index.complete(context).contains("\$query->getFieldForCondition('id')"))
    }

    @Test fun plainStringIsNotClaimedAsAMappedField() {
        assertNull(QueryContextResolver.resolve(prefix +
            "\$query->where()->firstCondition('user.email"))
    }

    @Test fun unrelatedFirstConditionIsNotClaimed() {
        assertNull(QueryContextResolver.resolve("<?php\n\$other->firstCondition("))
    }

    @Test fun thirdArgumentCanCompleteConditionField() {
        val context = assertNotNull(QueryContextResolver.resolve(prefix +
            "\$query->where()->firstCondition(\$query->getFieldForCondition('id'), ConditionOperatorType::equal, "))
        assertEquals("condition-value", context.kind)
        assertTrue(index.complete(context).contains("\$query->getFieldForCondition('email')"))
        assertTrue(index.complete(context).contains("null"))
    }

    @Test fun propertyInjectedManagerIsResolvedWithoutFactory() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n" +
            "class Example { private UserManager \$manager;\n" +
            "  public function work() {\n" +
            "    \$query = \$this->manager->createQueryBuilder('user');\n" +
            "    \$query->where()->firstCondition("
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertEquals("condition-field", context.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context.manager)
        assertEquals("\$query", context.queryReference)
        assertTrue(index.complete(context).contains("\$query->getFieldForCondition('email')"))
    }

    @Test fun promotedReadonlyManagerSupportsFluentCondition() {
        val source = "<?php\nuse Demo\\EntityManager\\UserManager;\n" +
            "class Example { public function __construct(private readonly UserManager \$manager) {}\n" +
            "  public function work() {\n" +
            "    \$this->manager->createQueryBuilder('user')->where()->firstCondition("
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertEquals("condition-field", context.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context.manager)
        assertEquals("\$this->manager->createQueryBuilder('user')", context.queryReference)
        assertTrue(index.complete(context).contains(
            "\$this->manager->createQueryBuilder('user')->getFieldForCondition('email')",
        ))
    }

}
