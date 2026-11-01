package dev.smallproject.ide

/**
 * Preserves character offsets while masking PHP comments and quoted literals.
 *
 * Used only by the standalone read-only coverage audit to avoid counting methods
 * in documentation, comments, SQL literals and other non-executable text.
 */
internal object PhpExecutableSourceMask {
    private val heredocOpen = Regex("""<<<[ \t]*(['"]?)([A-Za-z_]\w*)\1[ \t]*\r?\n""")

    fun mask(source: String): String {
        val output = source.toCharArray()
        var lineComment = false
        var blockComment = false
        var quote: Char? = null
        var escaped = false
        var i = 0
        fun blank(at: Int) {
            if (source[at] != '\n' && source[at] != '\r') output[at] = ' '
        }

        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            if (lineComment) {
                blank(i)
                if (c == '\n' || c == '\r') lineComment = false
                i++
                continue
            }
            if (blockComment) {
                blank(i)
                if (c == '*' && next == '/') {
                    blank(i + 1)
                    i += 2
                    blockComment = false
                } else i++
                continue
            }
            if (quote != null) {
                blank(i)
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == quote) quote = null
                i++
                continue
            }

            // A heredoc / nowdoc body is a PHP string, not executable method calls.
            if (c == '<' && source.startsWith("<<<", i)) {
                val opening = heredocOpen.find(source, i)?.takeIf { it.range.first == i }
                if (opening != null) {
                    val delimiter = Regex.escape(opening.groupValues[2])
                    val closing = Regex("""(?m)^[ \t]*""" + delimiter + """(?=[;,\)\]\r\n]|$)""")
                        .find(source, opening.range.last + 1)
                    val end = closing?.range?.last?.plus(1) ?: source.length
                    for (position in i until end) blank(position)
                    i = end
                    continue
                }
            }
            when {
                c == '/' && next == '/' -> { blank(i); blank(i + 1); lineComment = true; i += 2 }
                c == '/' && next == '*' -> { blank(i); blank(i + 1); blockComment = true; i += 2 }
                c == '#' && next != '[' -> { blank(i); lineComment = true; i++ }
                c == '\'' || c == '"' || c.code == 96 -> { blank(i); quote = c; i++ }
                else -> i++
            }
        }
        return String(output)
    }
}
