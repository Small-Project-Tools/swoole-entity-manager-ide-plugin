package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Regression for PHP typed handlers running before or after document insertion. */
class SmallCompletionAutoPopupTest {
    private val prefix = listOf(
        "<?php",
        "use Demo\\EntityManager\\UserManager;",
        "\$manager = \$factory->get(UserManager::class);",
        "\$query = \$manager->createQueryBuilder('user');",
    ).joinToString("\n")

    @Test fun fieldPopupWorksBeforeAndAfterOpeningParenthesis() {
        val without = prefix + "\n\$query->getFieldForCondition"
        assertEquals("field", SmallCompletionAutoPopup.contextForTypedText(without, '(')?.kind)
        assertEquals("field", SmallCompletionAutoPopup.contextForTypedText("$without(", '(')?.kind)
    }

    @Test fun factoryPopupWorksBeforeAndAfterOpeningParenthesis() {
        val before = "<?php\n\$manager = \$factory->get"
        assertEquals("manager", SmallCompletionAutoPopup.contextForTypedText(before, '(')?.kind)
        assertEquals("manager", SmallCompletionAutoPopup.contextForTypedText("$before(", '(')?.kind)
    }

    @Test fun conditionPopupHandlesOpeningParenAndNextArgumentComma() {
        val base = prefix + "\n\$query->where()->firstCondition"
        assertEquals("condition-field", SmallCompletionAutoPopup.contextForTypedText(base, '(')?.kind)
        val next = "$base(\$query->getFieldForCondition('id')"
        assertEquals("condition-operator", SmallCompletionAutoPopup.contextForTypedText(next, ',')?.kind)
    }

    @Test fun joinedAliasDotRequestsFieldPopupBeforeAndAfterTyping() {
        val query = prefix + "\n" +
            "\$query->leftJoin('user', 'company', 'owner')->endJoin();\n" +
            "\$query->getFieldForCondition(owner"
        assertEquals("owner.", SmallCompletionAutoPopup.contextForTypedText(query, '.')?.typedPrefix)
        assertEquals("owner.", SmallCompletionAutoPopup.contextForTypedText("$query.", '.')?.typedPrefix)
    }

    @Test fun unrelatedCallsAndExpressionsDoNotOpenSmallCompletion() {
        assertNull(SmallCompletionAutoPopup.contextForTypedText("<?php\n\$cacheFactory->get", '('))
        assertNull(SmallCompletionAutoPopup.contextForTypedText("<?php\n\$query->randomMethod", '('))
        assertNull(SmallCompletionAutoPopup.contextForTypedText(prefix + "\n\$query->getFieldForCondition", 'x'))
    }
}
