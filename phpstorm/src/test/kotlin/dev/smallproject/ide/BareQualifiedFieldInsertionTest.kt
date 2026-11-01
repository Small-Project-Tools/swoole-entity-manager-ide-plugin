package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BareQualifiedFieldInsertionTest {
    private fun apply(source: String, suggestion: String): String {
        val start = source.indexOf(suggestion)
        val end = start + suggestion.length
        val edit = assertNotNull(QualifiedFieldInsertion.plan(source, start, end, suggestion))
        return source.replaceRange(edit.start, edit.end, edit.replacement)
    }

    @Test fun bareDottedFieldBecomesTwoValidPhpArguments() {
        assertEquals(
            "\$query->getFieldForCondition('name', 'owner')",
            apply("\$query->getFieldForCondition(owner.name)", "owner.name"),
        )
    }

    @Test fun bareNamedArgumentIsAlsoConverted() {
        assertEquals(
            "\$query->getFieldForCondition(fieldName: 'name', entityAlias: 'owner')",
            apply("\$query->getFieldForCondition(fieldName: owner.name)", "owner.name"),
        )
    }

    @Test fun namedQuotedDottedFieldKeepsNamedAlias() {
        assertEquals(
            "\$query->getFieldForCondition(fieldName: 'name', entityAlias: 'owner')",
            apply("\$query->getFieldForCondition(fieldName: 'owner.name')", "owner.name"),
        )
    }
}
