package dev.smallproject.ide

import java.util.Random
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Deterministic fuzzing against malformed editor buffers and partial PHP files. */
class RcRobustnessFuzzTest {
    private val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_'\"()[]{}\\/:;?,.#\n "

    @Test fun thousandsOfPartialEditorBuffersNeverThrowOrHang() {
        val random = Random(0x5EED)
        val beginning = "<?php\nclass UserManager { public function run() {\n" +
            "\$query = \$this->createQueryBuilder('user');\n\$query->getFieldForCondition("
        repeat(3_000) { iteration ->
            val length = iteration % 97
            val suffix = buildString {
                repeat(length) { append(chars[random.nextInt(chars.length)]) }
            }
            val context = QueryContextResolver.resolve(beginning + suffix)
            // An incomplete buffer may resolve to a safe field context or no context.
            assertTrue(context == null || context.kind in setOf(
                "field", "alias", "relation", "projection", "parameter",
                "condition-field", "condition-value", "condition-operator",
            ))
        }
    }

    @Test fun sourceLimitAndParenthesisLimitAreEnforced() {
        assertNull(QueryContextResolver.resolve(" ".repeat(QueryContextResolver.MAX_SOURCE_LENGTH + 1)))
        val deep = "<?php\n\$query = \$this->createQueryBuilder('q');\n" +
            "(".repeat(129) + "\$query->getFieldForCondition("
        assertNull(QueryContextResolver.resolve(deep))
    }

    @Test fun malformedAttributesAndCommentsDoNotDestroyHealthyMetadata() {
        val random = Random(0xCAFE)
        val files = mutableMapOf(
            "Healthy.php" to """
                <?php
                namespace Fuzz;
                use Small\SwooleEntityManager\Entity\Attribute\Field;
                class Healthy { #[Field] private string ${'$'}code; }
            """.trimIndent(),
            "HealthyManager.php" to """
                <?php
                namespace Fuzz;
                use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
                #[Entity(Healthy::class)] class HealthyManager {}
            """.trimIndent(),
        )
        repeat(200) { i ->
            val noise = buildString {
                repeat(300) { append(chars[random.nextInt(chars.length)]) }
            }
            files["Malformed$i.php"] = "<?php\nnamespace Fuzz;\n" + noise
        }
        val index = NativePhpIndexer.index(files)
        assertEquals("Fuzz\\Healthy", index.managers["Fuzz\\HealthyManager"])
        assertEquals(setOf("code"), index.entities.getValue("Fuzz\\Healthy").fields)
    }
}
