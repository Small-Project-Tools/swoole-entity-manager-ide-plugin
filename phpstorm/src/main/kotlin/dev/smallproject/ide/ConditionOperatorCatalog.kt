package dev.smallproject.ide

/**
 * Core 3 ConditionOperatorType cases, taken from the public Small Entity Manager API.
 * A field operand must be a ConditionField; strings are interpreted as constants.
 */
internal object ConditionOperatorCatalog {
    val cases = listOf(
        "equal", "inferior", "superior", "inferiorOrEqual", "superiorOrEqual",
        "notEqual", "like", "notLike", "regex", "isNull", "isNotNull",
        "exists", "notExists", "in", "notIn",
    )
}
