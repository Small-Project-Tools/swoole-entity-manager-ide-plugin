package dev.smallproject.ide

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Generic regression for the actual failure: basic completion in an empty inner argument. */
class UnquotedCompletionEndToEndTest {
    @Test fun nestedEmptyFieldCallProducesMappedFieldSuggestionsWithoutPhp() {
        val source = "<?php\n" +
            "namespace Demo;\n" +
            "use Demo\\EntityManager\\UserManager;\n" +
            "\$manager = \$factory->get(UserManager::class);\n" +
            "\$query = \$manager->createQueryBuilder('user')\n" +
            "    ->leftJoin('user', 'company', 'owner')->endJoin();\n" +
            "\$query->where()->firstCondition(\$query->getFieldForCondition("

        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertEquals("field", context.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context.manager)
        assertEquals("user", context.rootAlias)
        assertEquals(1, context.joins.size)

        val index = NativeProjectScanner.scan(File("../tests/Functional/Fixtures/Project"))
        val suggestions = index.complete(context)
        assertTrue("email" in suggestions)
        assertTrue("id" in suggestions)
        assertTrue(suggestions.size >= 4)
    }
}
