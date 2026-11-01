package dev.smallproject.ide

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic lexer tests: all returned offsets must still reference the original text. */
class PhpExecutableSourceMaskRcTest {
    private val examples = listOf(
        "// ->firstCondition('bad')" to "line comment",
        "# ->innerJoin('bad')" to "hash comment",
        "/* ->andCondition('bad') */" to "block comment",
        "/** phpdoc ->getFieldForCondition('bad') */" to "phpdoc",
        "'->firstCondition(' " to "quoted string",
        "\"->addOrderBy(\" " to "double-quoted string",
        "<<<SQL\n->getFieldForCondition('bad')\nSQL;" to "heredoc",
        "<<<'BLOCK'\n->firstCondition('bad')\nBLOCK;" to "nowdoc",
        "<<<\"BLOCK\"\n->setParameter('bad')\nBLOCK;" to "quoted heredoc",
        "<<<BLOCK\n->leftJoin('bad')\n  BLOCK;" to "indented heredoc",
    )

    @TestFactory fun nonExecutableMethodTokensAreMasked(): List<DynamicTest> =
        examples.map { (source, reason) ->
            DynamicTest.dynamicTest(reason) {
                val masked = PhpExecutableSourceMask.mask("<?php\n" + source)
                assertEquals("<?php\n".length + source.length, masked.length)
                assertFalse(masked.contains("->"))
            }
        }

    @Test fun realPhpMethodAfterCommentsAndStringsIsRetained() {
        val code = """
            <?php
            #[Field(type: 'text')]
            class Example {
                // unmatched quote ' and dummy ->firstCondition(
                /* bad string: " */
                function run() {
                    $this->setParameter('key', 'value');
                    $this->getFieldForCondition("name");
                }
            }
        """.trimIndent()
        val masked = PhpExecutableSourceMask.mask(code)
        assertEquals(code.length, masked.length)
        assertTrue(masked.contains("->setParameter("))
        assertTrue(masked.contains("->getFieldForCondition("))
        assertFalse(masked.contains("->firstCondition("))
        assertTrue(masked.contains("#[Field("))
    }

    @Test fun unclosedBlockCommentMasksRestOfFile() {
        val masked = PhpExecutableSourceMask.mask("<?php\n/* unfinished\n->firstCondition(")
        assertFalse(masked.contains("->firstCondition("))
    }

    @Test fun phpStringsKeepLineBreakPositions() {
        val code = "<?php\n\$x = \"line 1\n->firstCondition(\nline 3\";\n\$q->getFieldForCondition("
        val masked = PhpExecutableSourceMask.mask(code)
        assertEquals(code.length, masked.length)
        assertEquals(code.indices.filter { code[it] == '\n' }, masked.indices.filter { masked[it] == '\n' })
        assertTrue(masked.contains("\$q->getFieldForCondition("))
        assertFalse(masked.contains("->firstCondition("))
    }

    @Test fun quotedUrlsNeverBecomeLineComments() {
        val code = "<?php\n\$url = 'https://example.invalid/path';\n\$query->getFieldForCondition("
        assertTrue(PhpExecutableSourceMask.mask(code).contains("\$query->getFieldForCondition("))
    }
}
