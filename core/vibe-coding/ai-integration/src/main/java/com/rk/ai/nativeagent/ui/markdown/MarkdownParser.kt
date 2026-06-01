package com.rk.ai.nativeagent.ui.markdown

sealed interface MarkdownBlock {
    data class Paragraph(val text: String, val inlineStyles: List<InlineStyle> = emptyList()) : MarkdownBlock
    data class Heading(val level: Int, val text: String, val inlineStyles: List<InlineStyle> = emptyList()) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data class InlineCode(val code: String) : MarkdownBlock
    data class UnorderedList(val items: List<List<InlineStyle>>) : MarkdownBlock
    data class OrderedList(val items: List<List<InlineStyle>>, val start: Int = 1) : MarkdownBlock
    data class BlockQuote(val text: String, val inlineStyles: List<InlineStyle> = emptyList()) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
    data class Image(val alt: String, val url: String) : MarkdownBlock
    data class Link(val text: String, val url: String) : MarkdownBlock
}

data class InlineStyle(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    val link: String? = null,
)

object MarkdownParser {

    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = markdown.split("\n")
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            when {
                // Code block
                line.trimStart().startsWith("```") -> {
                    val language = line.trimStart().removePrefix("```").trim().ifBlank { null }
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    i++ // skip closing ```
                    blocks.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
                }

                // Heading
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length
                    val text = line.drop(level).trim()
                    val styles = parseInlineStyles(text)
                    blocks.add(MarkdownBlock.Heading(level, text, styles))
                    i++
                }

                // Horizontal rule
                line.trimStart().matches(Regex("^-{3,}$|^\\*{3,}$|^_{3,}$")) -> {
                    blocks.add(MarkdownBlock.HorizontalRule)
                    i++
                }

                // Blockquote
                line.trimStart().startsWith(">") -> {
                    val text = line.trimStart().removePrefix(">").trim()
                    val styles = parseInlineStyles(text)
                    blocks.add(MarkdownBlock.BlockQuote(text, styles))
                    i++
                }

                // Unordered list
                line.trimStart().matches(Regex("^[-*+]\\s.*")) -> {
                    val items = mutableListOf<List<InlineStyle>>()
                    while (i < lines.size && lines[i].trimStart().matches(Regex("^[-*+]\\s.*"))) {
                        val text = lines[i].trimStart().removePrefix(Regex("[-*+]\\s")).trim()
                        items.add(parseInlineStyles(text))
                        i++
                    }
                    blocks.add(MarkdownBlock.UnorderedList(items))
                }

                // Ordered list
                line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                    val items = mutableListOf<List<InlineStyle>>()
                    val firstNum = line.trimStart().takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                    while (i < lines.size && lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*"))) {
                        val text = lines[i].trimStart().removePrefix(Regex("\\d+\\.\\s")).trim()
                        items.add(parseInlineStyles(text))
                        i++
                    }
                    blocks.add(MarkdownBlock.OrderedList(items, firstNum))
                }

                // Table
                line.contains("|") && i + 1 < lines.size &&
                    lines[i + 1].trimStart().matches(Regex("^[|\\s:-]+$")) -> {
                    val headers = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    i += 2
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].contains("|")) {
                        val cells = lines[i].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                        if (cells.isNotEmpty()) rows.add(cells)
                        i++
                    }
                    blocks.add(MarkdownBlock.Table(headers, rows))
                }

                // Blank line
                line.isBlank() -> {
                    i++
                }

                // Paragraph (default)
                else -> {
                    val paraLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].isNotBlank() &&
                        !lines[i].startsWith("#") &&
                        !lines[i].trimStart().startsWith("```") &&
                        !lines[i].trimStart().startsWith(">") &&
                        !lines[i].trimStart().matches(Regex("^[-*+]\\s.*")) &&
                        !lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*")) &&
                        !lines[i].trimStart().matches(Regex("^-{3,}$|^\\*{3,}$|^_{3,}$")) &&
                        !(lines[i].contains("|") && i + 1 < lines.size && lines[i + 1].trimStart()
                            .matches(Regex("^[|\\s:-]+$")))
                    ) {
                        paraLines.add(lines[i])
                        i++
                    }
                    val text = paraLines.joinToString(" ")
                    val styles = parseInlineStyles(text)
                    blocks.add(MarkdownBlock.Paragraph(text, styles))
                }
            }
        }

        return blocks
    }

    fun parseInlineStyles(text: String): List<InlineStyle> {
        val segments = mutableListOf<InlineStyle>()
        var i = 0
        while (i < text.length) {
            when {
                // Inline code
                text[i] == '`' && i + 1 < text.length && text[i + 1] != '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end >= 0) {
                        segments.add(InlineStyle(text = text.substring(i + 1, end), code = true))
                        i = end + 1
                    } else {
                        segments.add(InlineStyle(text = text[i].toString())); i++
                    }
                }
                // Image ![alt](url)
                text.startsWith("![", i) -> {
                    val closeBracket = text.indexOf("](", i + 2)
                    val closeParen = if (closeBracket >= 0) text.indexOf(')', closeBracket + 2) else -1
                    if (closeBracket >= 0 && closeParen >= 0) {
                        val alt = text.substring(i + 2, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        segments.add(InlineStyle(text = "[Image: $alt]", link = url))
                        i = closeParen + 1
                    } else {
                        segments.add(InlineStyle(text = text[i].toString())); i++
                    }
                }
                // Link [text](url)
                text[i] == '[' -> {
                    val closeBracket = text.indexOf("](", i + 1)
                    val closeParen = if (closeBracket >= 0) text.indexOf(')', closeBracket + 2) else -1
                    if (closeBracket >= 0 && closeParen >= 0) {
                        val linkText = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        segments.add(InlineStyle(text = linkText, link = url))
                        i = closeParen + 1
                    } else {
                        segments.add(InlineStyle(text = text[i].toString())); i++
                    }
                }
                // Strikethrough ~~text~~
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end >= 0) {
                        segments.add(InlineStyle(text = text.substring(i + 2, end), strikethrough = true))
                        i = end + 2
                    } else {
                        segments.add(InlineStyle(text = "~")); i++
                    }
                }
                // Bold **text** or __text__
                (text.startsWith("**", i) || text.startsWith("__", i)) -> {
                    val delimiter = text.substring(i, i + 2)
                    val end = text.indexOf(delimiter, i + 2)
                    if (end >= 0) {
                        val inner = text.substring(i + 2, end)
                        // Check for nested italic
                        if (inner.contains("*") && !inner.contains("**")) {
                            segments.add(InlineStyle(text = inner, bold = true, italic = true))
                        } else {
                            segments.add(InlineStyle(text = inner, bold = true))
                        }
                        i = end + 2
                    } else {
                        segments.add(InlineStyle(text = text[i].toString())); i++
                    }
                }
                // Italic *text* or _text_
                text[i] == '*' || text[i] == '_' -> {
                    val end = text.indexOf(text[i], i + 1)
                    if (end >= 0 && end + 1 < text.length && text[end + 1] != text[i] &&
                        end > i + 1
                    ) {
                        segments.add(InlineStyle(text = text.substring(i + 1, end), italic = true))
                        i = end + 1
                    } else {
                        segments.add(InlineStyle(text = text[i].toString())); i++
                    }
                }
                // HTML entity
                text[i] == '&' -> {
                    val semi = text.indexOf(';', i)
                    if (semi >= 0 && semi - i <= 8) {
                        val entity = text.substring(i, semi + 1)
                        val decoded = when (entity) {
                            "&amp;" -> "&"; "&lt;" -> "<"; "&gt;" -> ">"
                            "&quot;" -> "\""; "&#39;" -> "'"
                            else -> entity
                        }
                        segments.add(InlineStyle(text = decoded))
                        i = semi + 1
                    } else {
                        segments.add(InlineStyle(text = text[i].toString())); i++
                    }
                }
                else -> {
                    segments.add(InlineStyle(text = text[i].toString())); i++
                }
            }
        }
        return segments
    }

    private fun String.removePrefix(regex: Regex): String =
        regex.find(this)?.let { drop(it.value.length) } ?: this
}
