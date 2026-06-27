package com.rk.tabs.markdown

import java.util.Locale

/**
 * A small, dependency-free Markdown → HTML converter aimed at matching GitHub's rendered output as
 * closely as is practical offline. It supports the GitHub-flavored constructs people actually use in
 * READMEs:
 *
 *  - ATX headings (`#`..`######`) with GitHub's auto-generated anchor ids
 *  - Paragraphs with soft/hard line breaks
 *  - Bold, italic, bold-italic, strikethrough and inline `code`
 *  - Fenced code blocks (``` / ~~~) with a language class (escaped, no JS highlighter)
 *  - Block quotes (nestable)
 *  - Ordered / unordered lists, nested by indentation, plus task lists (`- [ ]` / `- [x]`)
 *  - GFM tables with column alignment
 *  - Horizontal rules
 *  - Links, autolinks (`<url>`) and images
 *
 * Everything is HTML-escaped, `javascript:` URLs are stripped, and the output is rendered in a
 * WebView with JavaScript disabled, so untrusted Markdown can't execute code.
 */
object MarkdownToHtml {

    fun convert(markdown: String): String {
        val normalized = markdown.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n")
        val sb = StringBuilder()
        parseBlocks(lines, 0, lines.size, sb)
        return sb.toString()
    }

    // ---- block parsing --------------------------------------------------------

    private fun parseBlocks(lines: List<String>, start: Int, end: Int, out: StringBuilder) {
        var i = start
        while (i < end) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Fenced code block
            val fence = fenceMarker(trimmed)
            if (fence != null) {
                i = parseFencedCode(lines, i, end, fence, out)
                continue
            }

            // ATX heading
            val heading = ATX_HEADING.matchEntire(trimmed)
            if (heading != null) {
                val level = heading.groupValues[1].length
                val text = heading.groupValues[2].trim()
                val id = slug(text)
                out.append("<h$level id=\"$id\">").append(renderInline(text)).append("</h$level>\n")
                i++
                continue
            }

            // Horizontal rule
            if (isHorizontalRule(trimmed)) {
                out.append("<hr>\n")
                i++
                continue
            }

            // Block quote
            if (trimmed.startsWith(">")) {
                i = parseBlockQuote(lines, i, end, out)
                continue
            }

            // GFM table (needs a delimiter row on the next line)
            if (line.contains('|') && i + 1 < end && isTableDelimiter(lines[i + 1])) {
                i = parseTable(lines, i, end, out)
                continue
            }

            // Lists
            if (isListItem(trimmed)) {
                i = parseList(lines, i, end, indentOf(line), out)
                continue
            }

            // Paragraph
            i = parseParagraph(lines, i, end, out)
        }
    }

    private fun parseParagraph(lines: List<String>, start: Int, end: Int, out: StringBuilder): Int {
        val collected = StringBuilder()
        var i = start
        while (i < end) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || isBlockStart(lines, i, end)) break
            if (collected.isNotEmpty()) collected.append('\n')
            // Hard line break: two trailing spaces or a trailing backslash.
            val hard = line.endsWith("  ") || trimmed.endsWith("\\")
            collected.append(renderInline(trimmed.removeSuffix("\\")))
            if (hard) collected.append("<br>")
            i++
        }
        if (i == start) i++ // safety: never loop forever
        out.append("<p>").append(collected).append("</p>\n")
        return i
    }

    private fun parseFencedCode(
        lines: List<String>,
        start: Int,
        end: Int,
        fence: String,
        out: StringBuilder,
    ): Int {
        val first = lines[start].trim()
        val lang = first.removePrefix(fence).trim().substringBefore(' ')
        val body = StringBuilder()
        var i = start + 1
        while (i < end) {
            val line = lines[i]
            if (line.trim().startsWith(fence) && line.trim().all { it == fence[0] }) {
                i++
                break
            }
            if (body.isNotEmpty()) body.append('\n')
            body.append(line)
            i++
        }
        val cls = if (lang.isNotEmpty()) " class=\"language-${escapeAttr(lang)}\"" else ""
        out.append("<pre><code$cls>").append(escapeHtml(body.toString())).append("</code></pre>\n")
        return i
    }

    private fun parseBlockQuote(lines: List<String>, start: Int, end: Int, out: StringBuilder): Int {
        val inner = mutableListOf<String>()
        var i = start
        while (i < end) {
            val trimmed = lines[i].trimStart()
            if (!trimmed.startsWith(">")) break
            inner.add(trimmed.removePrefix(">").removePrefix(" "))
            i++
        }
        out.append("<blockquote>\n")
        parseBlocks(inner, 0, inner.size, out)
        out.append("</blockquote>\n")
        return i
    }

    private fun parseList(
        lines: List<String>,
        start: Int,
        end: Int,
        minIndent: Int,
        out: StringBuilder,
    ): Int {
        val firstTrim = lines[start].trim()
        val ordered = ORDERED_ITEM.containsMatchIn(firstTrim)
        val tag = if (ordered) "ol" else "ul"
        out.append("<$tag>\n")
        var i = start
        while (i < end) {
            val line = lines[i]
            if (line.isBlank()) {
                // Allow a single blank line between items (loose lists).
                var j = i + 1
                while (j < end && lines[j].isBlank()) j++
                if (j < end && indentOf(lines[j]) >= minIndent && isListItem(lines[j].trim())) {
                    i = j
                    continue
                }
                break
            }
            val indent = indentOf(line)
            val itemTrim = line.trim()
            if (indent < minIndent || !isListItem(itemTrim)) break

            val match = LIST_ITEM.matchEntire(itemTrim) ?: break
            var content = match.groupValues[2]

            // Task list checkbox.
            var checkbox = ""
            val task = TASK_ITEM.matchEntire(content)
            if (task != null) {
                val checked = task.groupValues[1].lowercase(Locale.ROOT) == "x"
                checkbox =
                    "<input type=\"checkbox\" disabled${if (checked) " checked" else ""}> "
                content = task.groupValues[2]
            }

            i++

            // Gather continuation lines and nested lists (more indented than this item).
            val continuation = StringBuilder(content)
            val nested = StringBuilder()
            while (i < end) {
                val l = lines[i]
                if (l.isBlank()) {
                    var j = i + 1
                    while (j < end && lines[j].isBlank()) j++
                    if (j < end && indentOf(lines[j]) > indent && isListItem(lines[j].trim())) {
                        i = parseList(lines, j, end, indentOf(lines[j]), nested)
                        continue
                    }
                    break
                }
                val li = indentOf(l)
                if (li <= indent) break
                if (isListItem(l.trim())) {
                    i = parseList(lines, i, end, li, nested)
                } else {
                    continuation.append(' ').append(l.trim())
                    i++
                }
            }

            out.append(if (checkbox.isNotEmpty()) "<li class=\"task-list-item\">" else "<li>")
                .append(checkbox)
                .append(renderInline(continuation.toString()))
                .append(nested)
                .append("</li>\n")
        }
        out.append("</$tag>\n")
        return i
    }

    private fun parseTable(lines: List<String>, start: Int, end: Int, out: StringBuilder): Int {
        val header = splitRow(lines[start])
        val aligns = splitRow(lines[start + 1]).map { cell ->
            val c = cell.trim()
            when {
                c.startsWith(":") && c.endsWith(":") -> "center"
                c.endsWith(":") -> "right"
                c.startsWith(":") -> "left"
                else -> ""
            }
        }
        out.append("<table>\n<thead>\n<tr>")
        header.forEachIndexed { idx, cell ->
            val align = aligns.getOrNull(idx).orEmpty()
            val style = if (align.isNotEmpty()) " style=\"text-align:$align\"" else ""
            out.append("<th$style>").append(renderInline(cell.trim())).append("</th>")
        }
        out.append("</tr>\n</thead>\n<tbody>\n")
        var i = start + 2
        while (i < end) {
            val line = lines[i]
            if (line.isBlank() || !line.contains('|')) break
            val cells = splitRow(line)
            out.append("<tr>")
            for (idx in header.indices) {
                val align = aligns.getOrNull(idx).orEmpty()
                val style = if (align.isNotEmpty()) " style=\"text-align:$align\"" else ""
                out.append("<td$style>").append(renderInline(cells.getOrNull(idx)?.trim() ?: "")).append("</td>")
            }
            out.append("</tr>\n")
            i++
        }
        out.append("</tbody>\n</table>\n")
        return i
    }

    // ---- inline parsing -------------------------------------------------------

    private fun renderInline(text: String): String {
        val placeholders = ArrayList<String>()
        fun ph(html: String): String {
            placeholders.add(html)
            return "\u0000${placeholders.size - 1}\u0000"
        }

        val sb = StringBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < n && text[i + 1] in ESCAPABLE -> {
                    sb.append(escapeHtml(text[i + 1].toString()))
                    i += 2
                }
                c == '`' -> {
                    var ticks = 0
                    while (i + ticks < n && text[i + ticks] == '`') ticks++
                    val fenceStr = "`".repeat(ticks)
                    val closeIdx = text.indexOf(fenceStr, i + ticks)
                    if (closeIdx >= 0) {
                        val code = text.substring(i + ticks, closeIdx).trim()
                        sb.append(ph("<code>${escapeHtml(code)}</code>"))
                        i = closeIdx + ticks
                    } else {
                        sb.append(escapeHtml(fenceStr))
                        i += ticks
                    }
                }
                c == '!' && i + 1 < n && text[i + 1] == '[' -> {
                    val parsed = parseLinkOrImage(text, i + 1)
                    if (parsed != null) {
                        val (label, url, nextIndex) = parsed
                        sb.append(
                            ph("<img src=\"${escapeAttr(safeUrl(url))}\" alt=\"${escapeAttr(label)}\">")
                        )
                        i = nextIndex
                    } else {
                        sb.append(escapeHtml("!"))
                        i++
                    }
                }
                c == '[' -> {
                    val parsed = parseLinkOrImage(text, i)
                    if (parsed != null) {
                        val (label, url, nextIndex) = parsed
                        sb.append(
                            ph("<a href=\"${escapeAttr(safeUrl(url))}\">${renderInline(label)}</a>")
                        )
                        i = nextIndex
                    } else {
                        sb.append(escapeHtml("["))
                        i++
                    }
                }
                c == '<' -> {
                    val close = text.indexOf('>', i + 1)
                    val candidate = if (close > i) text.substring(i + 1, close) else ""
                    if (close > i && AUTOLINK.matches(candidate)) {
                        sb.append(ph("<a href=\"${escapeAttr(safeUrl(candidate))}\">${escapeHtml(candidate)}</a>"))
                        i = close + 1
                    } else {
                        sb.append("&lt;")
                        i++
                    }
                }
                c == '&' -> {
                    sb.append("&amp;"); i++
                }
                c == '>' -> {
                    sb.append("&gt;"); i++
                }
                else -> {
                    sb.append(c); i++
                }
            }
        }

        // Emphasis / strikethrough on the escaped text (placeholders are inert).
        var result = sb.toString()
        result = BOLD_ITALIC_STAR.replace(result, "<strong><em>$1</em></strong>")
        result = BOLD_STAR.replace(result, "<strong>$1</strong>")
        result = BOLD_UNDERSCORE.replace(result, "<strong>$1</strong>")
        result = STRIKETHROUGH.replace(result, "<del>$1</del>")
        result = ITALIC_STAR.replace(result, "<em>$1</em>")
        result = ITALIC_UNDERSCORE.replace(result, "<em>$1</em>")

        // Restore placeholders.
        val restored = StringBuilder()
        var k = 0
        while (k < result.length) {
            val c = result[k]
            if (c == '\u0000') {
                val close = result.indexOf('\u0000', k + 1)
                if (close > k) {
                    val idx = result.substring(k + 1, close).toIntOrNull()
                    if (idx != null && idx in placeholders.indices) {
                        restored.append(placeholders[idx])
                        k = close + 1
                        continue
                    }
                }
            }
            restored.append(c)
            k++
        }
        return restored.toString()
    }

    /** Parse `[label](url)` starting at the `[`; returns (label, url, indexAfter) or null. */
    private fun parseLinkOrImage(text: String, openBracket: Int): Triple<String, String, Int>? {
        val closeBracket = matchBracket(text, openBracket, '[', ']') ?: return null
        if (closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') return null
        val closeParen = matchBracket(text, closeBracket + 1, '(', ')') ?: return null
        val label = text.substring(openBracket + 1, closeBracket)
        var url = text.substring(closeBracket + 2, closeParen).trim()
        // Strip an optional ("title").
        val space = url.indexOf(' ')
        if (space > 0) url = url.substring(0, space)
        return Triple(label, url, closeParen + 1)
    }

    private fun matchBracket(text: String, start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var i = start
        while (i < text.length) {
            when (text[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    // ---- helpers --------------------------------------------------------------

    private fun fenceMarker(trimmed: String): String? =
        when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }

    private fun isHorizontalRule(trimmed: String): Boolean {
        val compact = trimmed.replace(" ", "")
        return compact.length >= 3 &&
            (compact.all { it == '-' } || compact.all { it == '*' } || compact.all { it == '_' })
    }

    private fun isListItem(trimmed: String): Boolean = LIST_ITEM.matches(trimmed)

    private fun indentOf(line: String): Int {
        var count = 0
        for (c in line) {
            when (c) {
                ' ' -> count++
                '\t' -> count += 4
                else -> return count
            }
        }
        return count
    }

    private fun isBlockStart(lines: List<String>, i: Int, end: Int): Boolean {
        val trimmed = lines[i].trim()
        if (trimmed.isEmpty()) return true
        if (fenceMarker(trimmed) != null) return true
        if (ATX_HEADING.matches(trimmed)) return true
        if (isHorizontalRule(trimmed)) return true
        if (trimmed.startsWith(">")) return true
        if (isListItem(trimmed)) return true
        if (lines[i].contains('|') && i + 1 < end && isTableDelimiter(lines[i + 1])) return true
        return false
    }

    private fun isTableDelimiter(line: String): Boolean {
        val t = line.trim()
        if (!t.contains('-')) return false
        return t.all { it == '|' || it == '-' || it == ':' || it == ' ' }
    }

    private fun splitRow(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.substring(0, t.length - 1)
        // Split on unescaped pipes.
        val cells = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length && t[i + 1] == '|') {
                cur.append('|'); i += 2; continue
            }
            if (c == '|') {
                cells.add(cur.toString()); cur.clear(); i++; continue
            }
            cur.append(c); i++
        }
        cells.add(cur.toString())
        return cells
    }

    private fun slug(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 _-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")

    private fun safeUrl(url: String): String {
        val trimmed = url.trim()
        val lower = trimmed.lowercase(Locale.ROOT).replace(Regex("\\s"), "")
        return if (lower.startsWith("javascript:") || lower.startsWith("data:text/html")) "#" else trimmed
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeAttr(s: String): String =
        escapeHtml(s).replace("\"", "&quot;").replace("'", "&#39;")

    private val ATX_HEADING = Regex("^(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$")
    private val LIST_ITEM = Regex("^([-*+]|\\d+[.)])\\s+(.*)$")
    private val ORDERED_ITEM = Regex("^\\d+[.)]\\s+")
    private val TASK_ITEM = Regex("^\\[([ xX])\\]\\s+(.*)$")
    private val AUTOLINK = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+$|^mailto:\\S+$")
    private val BOLD_ITALIC_STAR = Regex("\\*\\*\\*(.+?)\\*\\*\\*")
    private val BOLD_STAR = Regex("\\*\\*(.+?)\\*\\*")
    private val BOLD_UNDERSCORE = Regex("__(.+?)__")
    private val STRIKETHROUGH = Regex("~~(.+?)~~")
    private val ITALIC_STAR = Regex("\\*(.+?)\\*")
    private val ITALIC_UNDERSCORE = Regex("(?<![\\w])_(.+?)_(?![\\w])")
    private val ESCAPABLE = "\\`*_{}[]()#+-.!~|>".toSet()
}
