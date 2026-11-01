package dev.smallproject.ide

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Synthetic PHP only: comments must never corrupt active call identification. */
class PhpCommentLexingRcTest {
    private val prefix = """
        <?php
        namespace RcExamples\EntityManager;
        class ExampleManager {
          public function list(): void {
            ${'$'}query = ${'$'}this->createQueryBuilder('item');
    """.trimIndent() + "\n"

    private val comments = listOf(
        "// apostrophe ' not balanced",
        "// double quote \" not balanced",
        "// parentheses )(( and a semicolon ;",
        "// fake callback ->getFieldForCondition(",
        "# hash comment ' or \" or (",
        "/* block comment with unmatched ' quote */",
        "/* block comment with unmatched \" quote */",
        "/** documentation ' quote and ) ->firstCondition( */",
        "/** several lines\n * with ' and \" and ->join(\n */",
        "/* escaped \\\" and \\', not PHP strings */",
        "// PHP example: \$query->where()->firstCondition('",
        "// C-style URL http://example.invalid/' quote",
        "// fake <script> \" and )",
        "/* multiline\n * fake: ->setParameter(':id', 'value')\n * '; \" //\n */",
        "# include parentheses )(((((( \" ",
        "// dummy: \$query = \$other->join('bad', 'bad');",
    )

    private val contexts = mapOf(
        "getFieldForCondition(" to "field",
        "firstCondition(" to "condition-field",
        "andCondition(" to "condition-field",
        "addOrderBy(" to "field",
        "innerJoin(" to "alias",
    )

    @TestFactory fun commentsDoNotCorruptSmallMethodParsing(): List<DynamicTest> =
        comments.flatMapIndexed { i, comment ->
            contexts.map { (method, kind) ->
                DynamicTest.dynamicTest("comment=$i method=$method") {
                    val source = prefix + comment + "\n" +
                        "\$query->" + (if (method.endsWith("Condition(")) "where()->" else "") + method
                    val context = assertNotNull(QueryContextResolver.resolve(source))
                    assertEquals(kind, context.kind)
                    assertEquals("RcExamples\\EntityManager\\ExampleManager", context.manager)
                    assertEquals("item", context.rootAlias)
                }
            }
        }

    @Test fun quotedSlashesAndBlockMarkersAreNotComments() {
        val source = prefix +
            "\$url = \"https://example.invalid/it\\'s/test\";\n" +
            "\$marker = \"/* not a comment */\";\n" +
            "\$query->getFieldForCondition("
        val context = assertNotNull(QueryContextResolver.resolve(source))
        assertEquals("field", context.kind)
    }

    @Test fun commentsCannotInjectFakeQueryableCalls() {
        val source = prefix + "\n// \$query->getFieldForCondition(\n" +
            "/* \$query->firstCondition( */"
        assertNull(QueryContextResolver.resolve(source))
    }

    @Test fun hashAttributesDoNotSwallowSubsequentCalls() {
        val source = prefix + "  #[Field(type: 'string')]\n" +
            "  private string \$code;\n  \$query->getFieldForCondition("
        assertEquals("field", QueryContextResolver.resolve(source)?.kind)
    }

    @Test fun incompleteLineCommentBeforeCaretDoesNotClaimCompletions() {
        val source = prefix + "// \$query->getFieldForCondition("
        assertNull(QueryContextResolver.resolve(source))
    }
    @Test fun heredocAndNowdocBodiesCannotCorruptActiveQueries() {
        for (opening in listOf("<<<SQL", "<<<'SQL'", "<<<\"SQL\"")) {
            val source = prefix + "\$sql = $opening\n" +
                "  not executable: ->firstCondition(' and ) \n" +
                "  SQL;\n" +
                "\$query->getFieldForCondition("
            assertEquals("field", QueryContextResolver.resolve(source)?.kind)
        }
    }

}
