package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Bare join aliases must not be mistaken for unrelated PHP expressions. */
class SmallCompletionConfidenceTest {
    private val prefix = listOf(
        "<?php",
        "namespace Demo\\Smoke;",
        "use Demo\\EntityManager\\UserManager;",
        "\$manager = \$factory->get(UserManager::class);",
        "\$query = \$manager->createQueryBuilder('user');",
        "\$query->leftJoin('user', 'company', 'owner')->endJoin();",
    ).joinToString("\n")

    @Test fun joinedBareAliasAllowsAutoPopup() {
        assertTrue(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n\$query->getFieldForCondition(owner.",
        ))
    }

    @Test fun namedFirstArgumentAlsoAllowsAutoPopup() {
        assertTrue(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n\$query->getFieldForCondition(fieldName: owner.",
        ))
    }

    @Test fun rootAliasAllowsAutoPopup() {
        assertTrue(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n\$query->getFieldForCondition(user.",
        ))
    }

    @Test fun quotedFieldNeedsNoOverride() {
        assertFalse(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n\$query->getFieldForCondition('owner.",
        ))
    }

    @Test fun unboundAliasAndUnrelatedMethodDoNotOverride() {
        assertFalse(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n\$query->getFieldForCondition(unknown.",
        ))
        assertFalse(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n\$query->otherMethod(owner.",
        ))
        assertFalse(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            "<?php\n\$cache->get(owner.",
        ))
    }

    @Test fun commentsAndNestedExpressionsDoNotOverride() {
        assertFalse(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n// \$query->getFieldForCondition(owner.",
        ))
        assertFalse(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n\$query->getFieldForCondition(owner.name.",
        ))
        assertFalse(SmallCompletionConfidence.shouldForceSmallFieldPopup(
            prefix + "\n\$query->getFieldForCondition(",
        ))
    }
}
