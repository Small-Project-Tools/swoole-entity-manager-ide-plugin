package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PhpStringInsertionTest {
    private fun apply(source: String, suggestion: String): String {
        val start = source.indexOf(suggestion)
        val end = start + suggestion.length
        val edit = assertNotNull(PhpStringInsertion.plan(source, start, end, suggestion))
        return source.replaceRange(edit.start, edit.end, edit.replacement)
    }

    @Test fun emptyUnquotedFieldBecomesValidPhpString() {
        assertEquals(
            "\$query->getFieldForCondition('deletedAt')",
            apply("\$query->getFieldForCondition(deletedAt)", "deletedAt"),
        )
    }

    @Test fun emptyNamedParameterBecomesValidPhpString() {
        assertEquals(
            "\$query->getFieldForCondition(fieldName: 'email')",
            apply("\$query->getFieldForCondition(fieldName: email)", "email"),
        )
    }

    @Test fun bareJoinRelationGetsQuotedToo() {
        assertEquals(
            "\$query->leftJoin('owner')",
            apply("\$query->leftJoin(owner)", "owner"),
        )
    }

    @Test fun unfinishedQuotedFieldGetsClosingQuote() {
        val input = "\$query->getFieldForCondition('email)"
        val start = input.indexOf("email")
        val edit = assertNotNull(PhpStringInsertion.plan(input, start, start + 5, "email"))
        assertEquals("\$query->getFieldForCondition('email')", input.replaceRange(edit.start, edit.end, edit.replacement))
    }

    @Test fun existingQuoteIsNotDuplicated() {
        val input = "\$query->getFieldForCondition('email')"
        val start = input.indexOf("email")
        assertNull(PhpStringInsertion.plan(input, start, start + 5, "email"))
    }
}
