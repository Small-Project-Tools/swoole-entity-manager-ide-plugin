package dev.smallproject.ide

/**
 * Converts a dotted IDE lookup label into Core 3's two separate PHP arguments.
 * Condition fields accept (fieldName, entityAlias), never a dotted fieldName.
 */
internal object QualifiedFieldInsertion {
    data class Edit(val start: Int, val end: Int, val replacement: String) {
        val caretOffset: Int get() = start + replacement.length
    }

    private val identifier = Regex("""[A-Za-z_]\w*""")
    private val firstArgument = Regex("""getFieldForCondition\s*\(\s*(?:fieldName\s*:\s*)?$""")

    fun plan(source: String, startOffset: Int, tailOffset: Int, suggestion: String): Edit? {
        val separator = suggestion.indexOf('.')
        if (separator <= 0 || separator == suggestion.lastIndex) return null
        val alias = suggestion.substring(0, separator)
        val field = suggestion.substring(separator + 1)
        if (!identifier.matches(alias) || !identifier.matches(field)) return null
        if (startOffset !in 0..source.length || tailOffset !in startOffset..source.length) return null

        val left = source.lastIndexOf('\'', startOffset - 1)
        val right = source.lastIndexOf('"', startOffset - 1)
        val quoteStart = maxOf(left, right)
        if (quoteStart < 0) return null
        val quote = source[quoteStart]
        if (source.substring(quoteStart + 1, startOffset).any { it != '.' && !it.isLetterOrDigit() && it != '_' }) return null
        if (!firstArgument.containsMatchIn(source.substring(maxOf(0, quoteStart - 120), quoteStart))) return null

        // Replace the entire old string value, including a suffix beyond the caret.
        var closingQuote = -1
        for (i in tailOffset until minOf(source.length, tailOffset + 256)) {
            if (source[i] == '\n' || source[i] == ')') break
            if (source[i] == quote && (i == 0 || source[i - 1] != '\\')) {
                closingQuote = i
                break
            }
        }
        val alreadyHasAlias = closingQuote >= 0 &&
            source.substring(closingQuote + 1).trimStart().startsWith(',')
        val replacement = if (alreadyHasAlias) {
            "$field$quote"
        } else {
            "$field$quote, $quote$alias$quote"
        }
        val end = if (closingQuote >= 0) closingQuote + 1 else tailOffset
        return Edit(quoteStart + 1, end, replacement)
    }
}
