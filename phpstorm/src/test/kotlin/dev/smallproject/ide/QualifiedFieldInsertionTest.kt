package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QualifiedFieldInsertionTest {
    private fun rewrite(source: String, suggestion: String): String {
        val start = source.indexOf(suggestion)
        val end = start + suggestion.length
        val edit = assertNotNull(QualifiedFieldInsertion.plan(source, start, end, suggestion))
        return source.replaceRange(edit.start, edit.end, edit.replacement)
    }

    @Test fun turnsQuotedDottedFieldIntoValidCore3Arguments() {
        assertEquals(
            "\$query->getFieldForCondition('name', 'owner')",
            rewrite("\$query->getFieldForCondition('owner.name')", "owner.name"),
        )
    }

    @Test fun supportsTheRootAliasAndDoubleQuotes() {
        assertEquals(
            "\$query->getFieldForCondition(\"deletedAt\", \"user\")",
            rewrite("\$query->getFieldForCondition(\"user.deletedAt\")", "user.deletedAt"),
        )
    }

    @Test fun addsClosingQuoteForUnfinishedString() {
        assertEquals(
            "\$query->getFieldForCondition('email', 'user')",
            rewrite("\$query->getFieldForCondition('user.email)", "user.email"),
        )
    }

    @Test fun doesNotDuplicateExistingExplicitAlias() {
        assertEquals(
            "\$query->getFieldForCondition('name', 'owner')",
            rewrite("\$query->getFieldForCondition('owner.name', 'owner')", "owner.name"),
        )
    }

    @Test fun neverMutatesUnrelatedStringLiterals() {
        val code = "echo 'owner.name';"
        assertNull(QualifiedFieldInsertion.plan(
            code, code.indexOf("owner.name"), code.indexOf("owner.name") + "owner.name".length, "owner.name",
        ))
    }
}
