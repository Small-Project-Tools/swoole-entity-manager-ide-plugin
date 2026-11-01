package dev.smallproject.ide

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Query parameters require placeholder context, but not a Manager lookup. */
class ParameterCompletionRcTest {
    private fun queryWithPlaceholder(name: String) =
        "<?php\nclass Example {\npublic function run() {\n" +
        "\$query = \$this->createQueryBuilder('subject');\n" +
        "\$query->where()->firstCondition(\$query->getFieldForCondition('id'), " +
        "Operator::equal, ':$name');\n"

    @Test fun untypedQueryVariableStillCompletesParameters() {
        val source = queryWithPlaceholder("identifier") + "\$query->setParameter("
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertEquals("parameter", context.kind)
        assertEquals(listOf("identifier"), context.parameters)
        assertEquals(listOf("identifier"), NativeProjectIndex(emptyMap(), emptyMap()).complete(context))
    }

    @Test fun namedParameterCompletesWithoutLookingUpTheManager() {
        val source = queryWithPlaceholder("userId") + "\$query->setParameter(paramName: "
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertEquals("parameter", context.kind)
        assertEquals(listOf("userId"), context.parameters)
    }

    @Test fun valueArgumentNeverClaimsAParameterName() {
        val source = queryWithPlaceholder("userId") + "\$query->setParameter('userId', "
        assertNull(QueryContextResolver.resolve(source))
    }

    @Test fun unrelatedObjectWithoutAQueryDoesNotInjectCompletions() {
        val source = "<?php\nclass Other {\npublic function run() {\n" +
            "\$client->setParameter("
        assertNull(QueryContextResolver.resolve(source))
    }

    @Test fun placeholdersFromEarlierMethodsAreNotSuggested() {
        val source = queryWithPlaceholder("privateName") +
            "}\npublic function unrelated() {\n" +
            "\$query = \$this->createQueryBuilder('subject');\n" +
            "\$query->setParameter("
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertTrue(context.parameters.isEmpty())
    }

    @TestFactory fun manyDynamicParameterIdentifiers(): List<DynamicTest> =
        (0 until 75).map { number ->
            DynamicTest.dynamicTest("placeholder #$number") {
                val name = "parameter$number"
                val context = assertNotNull(QueryContextResolver.resolve(
                    queryWithPlaceholder(name) + "\$query->setParameter(",
                ))
                assertEquals("parameter", context.kind)
                assertEquals(listOf(name), context.parameters)
            }
        }
}
