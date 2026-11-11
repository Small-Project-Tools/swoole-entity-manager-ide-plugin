package dev.smallproject.ide

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Regression for real-editor completion with an existing join alias after the caret. */
class ExplicitJoinedFieldAliasCompletionTest {
    private val fixture = File("../tests/Functional/Fixtures/Project")

    private fun suggestions(call: String): Pair<QueryContext, List<String>> {
        val source = """
            <?php
            namespace Demo;
            use Demo\\EntityManager\\UserManager;
            ${'$'}manager = ${'$'}factory->get(UserManager::class);
            ${'$'}query = ${'$'}manager->createQueryBuilder('user');
            ${'$'}query->leftJoin('user', 'company', 'c')->endJoin();
            ${'$'}query->$call;
        """.trimIndent()
        val caret = source.indexOf("<caret>")
        assertTrue(caret >= 0)
        val before = source.substring(0, caret)
        val after = source.substring(caret + "<caret>".length)
        val context = assertNotNull(QueryContextResolver.resolve(before, after))
        val matches = NativeProjectScanner.scan(fixture).complete(context)
            .filter { it.startsWith(context.typedPrefix) }
        return context to matches
    }

    @Test fun positionalAliasAfterCaretCompletesJoinedField() {
        val (context, matches) = suggestions("getFieldForCondition('na<caret>me', 'c')")
        assertEquals("c", context.alias)
        assertEquals(listOf("name"), matches)
    }

    @Test fun namedAliasAfterCaretCompletesJoinedField() {
        val (context, matches) = suggestions("getFieldForCondition(fieldName: 'na<caret>me', entityAlias: 'c')")
        assertEquals("c", context.alias)
        assertEquals(listOf("name"), matches)
    }

    @Test fun namedAliasBeforeFieldIsRespected() {
        val (context, matches) = suggestions("getFieldForCondition(entityAlias: 'c', fieldName: 'na<caret>me')")
        assertEquals("c", context.alias)
        assertEquals(listOf("name"), matches)
    }

    @Test fun orderByUsesAlreadyWrittenJoinedAlias() {
        val (context, matches) = suggestions("addOrderBy('na<caret>me', 'c')")
        assertEquals("c", context.alias)
        assertEquals(listOf("name"), matches)
    }

    @Test fun unknownJoinAliasNeverFallsBackToRootFields() {
        val (context, matches) = suggestions("getFieldForCondition('na<caret>me', 'unknown')")
        assertEquals("unknown", context.alias)
        assertTrue(matches.isEmpty())
    }

    @Test fun missingAliasKeepsRootFieldCompletion() {
        val (context, matches) = suggestions("getFieldForCondition('em<caret>ail')")
        assertEquals(null, context.alias)
        assertEquals(listOf("email"), matches)
    }

    @Test fun laterCallsCannotLeakTheirAliasIntoCurrentCompletion() {
        val (context, matches) = suggestions("getFieldForCondition('em<caret>ail'); ${'$'}query->getFieldForCondition('name', 'c')")
        assertEquals(null, context.alias)
        assertEquals(listOf("email"), matches)
    }
}
