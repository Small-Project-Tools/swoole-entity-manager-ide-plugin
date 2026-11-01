package dev.smallproject.ide

/**
 * Generates valid PHP string literals when basic completion is invoked before
 * typing a quote. IntelliJ calls insertion handlers after it has inserted the
 * selected lookup text into the original editor document.
 */
internal object PhpStringInsertion {
    fun plan(source: String, startOffset: Int, tailOffset: Int, inserted: String): QualifiedFieldInsertion.Edit? {
        if (startOffset !in 0..source.length || tailOffset !in startOffset..source.length) return null
        val existingQuote = source.getOrNull(startOffset - 1)
        if (existingQuote != '\'' && existingQuote != '"') {
            val escaped = inserted.replace("\\", "\\\\").replace("'", "\\'")
            return QualifiedFieldInsertion.Edit(startOffset, tailOffset, "'$escaped'")
        }

        // A string may be started but not closed. Complete the missing quote
        // without duplicating the one inserted automatically by PhpStorm.
        for (index in tailOffset until minOf(source.length, tailOffset + 256)) {
            val char = source[index]
            if (char == existingQuote && (index == 0 || source[index - 1] != '\\')) return null
            if (char == ')' || char == ',' || char == ';' || char == '\n') break
        }
        return QualifiedFieldInsertion.Edit(tailOffset, tailOffset, existingQuote.toString())
    }
}
