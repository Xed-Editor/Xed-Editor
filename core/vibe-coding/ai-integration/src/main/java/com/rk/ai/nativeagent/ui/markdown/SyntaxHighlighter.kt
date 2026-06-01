package com.rk.ai.nativeagent.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object SyntaxHighlighter {

    private val KEYWORD = SpanStyle(color = Color(0xFFCC7832), fontWeight = FontWeight.Medium)
    private val STRING = SpanStyle(color = Color(0xFF6A8759))
    private val COMMENT = SpanStyle(color = Color(0xFF808080), fontWeight = FontWeight.Light)
    private val NUMBER = SpanStyle(color = Color(0xFF6897BB))
    private val ANNOTATION = SpanStyle(color = Color(0xFFBBB529))
    private val FUNCTION = SpanStyle(color = Color(0xFFFFC66D))
    private val TYPE = SpanStyle(color = Color(0xFFA9B7C6), fontWeight = FontWeight.Medium)
    private val OPERATOR = SpanStyle(color = Color(0xFF9876AA))
    private val PROPERTY = SpanStyle(color = Color(0xFF6C9EF8))
    private val DEFAULT = SpanStyle(color = Color(0xFFA9B7C6))
    private val XML_TAG = SpanStyle(color = Color(0xFFCC7832))
    private val XML_ATTR = SpanStyle(color = Color(0xFF6C9EF8))
    private val XML_VALUE = SpanStyle(color = Color(0xFF6A8759))
    private val YAML_KEY = SpanStyle(color = Color(0xFF6C9EF8))
    private val YAML_VALUE = SpanStyle(color = Color(0xFFA9B7C6))

    fun highlight(code: String, language: String?): AnnotatedString {
        val sb = StringBuilder()
        val spans = mutableListOf<Triple<SpanStyle, Int, Int>>()

        when {
            language in listOf("kotlin", "kts", "kt") -> highlightLanguage(sb, spans, code, kotlinKeywords, true)
            language in listOf("java") -> highlightLanguage(sb, spans, code, javaKeywords, true)
            language in listOf("python", "py") -> highlightLanguage(sb, spans, code, pythonKeywords, false)
            language in listOf("javascript", "js", "typescript", "ts", "jsx", "tsx") ->
                highlightLanguage(sb, spans, code, jsKeywords, false)
            language in listOf("go") -> highlightLanguage(sb, spans, code, goKeywords, false)
            language in listOf("rust", "rs") -> highlightLanguage(sb, spans, code, rustKeywords, false)
            language in listOf("shell", "bash", "sh", "zsh") -> highlightLanguage(sb, spans, code, shellKeywords, false)
            language in listOf("sql") -> highlightSql(sb, spans, code)
            language in listOf("xml", "html", "svg") -> highlightXml(sb, spans, code)
            language in listOf("json") -> highlightJson(sb, spans, code)
            language in listOf("css") -> highlightCss(sb, spans, code)
            language in listOf("yaml", "yml") -> highlightYaml(sb, spans, code)
            language in listOf("gradle", "groovy") -> highlightLanguage(sb, spans, code, gradleKeywords, false)
            language in listOf("properties", "conf") -> highlightProperties(sb, spans, code)
            language in listOf("diff", "patch") -> highlightDiff(sb, spans, code)
            else -> highlightFallback(sb, spans, code)
        }

        return buildAnnotatedString {
            append(sb.toString())
            spans.forEach { (style, start, end) ->
                if (start >= 0 && end <= sb.length && start < end) {
                    addStyle(style, start, end)
                }
            }
        }
    }

    private fun highlightLanguage(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>,
        code: String, keywords: Set<String>, hasAnnotations: Boolean,
    ) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) sb.append("\n")
            var i = 0
            while (i < line.length) {
                val start = sb.length
                when {
                    line.startsWith("//", i) -> {
                        sb.append(line.substring(i))
                        spans.add(Triple(COMMENT, start, sb.length))
                        i = line.length
                    }
                    line.startsWith("/*", i) -> {
                        val end = line.indexOf("*/", i + 2)
                        if (end >= 0) {
                            sb.append(line.substring(i, end + 2))
                            spans.add(Triple(COMMENT, start, sb.length))
                            i = end + 2
                        } else {
                            sb.append(line.substring(i))
                            spans.add(Triple(COMMENT, start, sb.length))
                            i = line.length
                        }
                    }
                    line[i] == '"' -> {
                        val end = findEndOfString(line, i, '"')
                        sb.append(line.substring(i, end))
                        spans.add(Triple(STRING, start, sb.length))
                        i = end
                    }
                    line[i] == '\'' -> {
                        val end = findEndOfString(line, i, '\'')
                        sb.append(line.substring(i, end))
                        spans.add(Triple(STRING, start, sb.length))
                        i = end
                    }
                    line[i] == '`' -> {
                        val end = line.indexOf('`', i + 1)
                        val endIdx = if (end >= 0) end + 1 else line.length
                        sb.append(line.substring(i, endIdx))
                        spans.add(Triple(STRING, start, sb.length))
                        i = endIdx
                    }
                    line[i] == '@' && hasAnnotations -> {
                        val end = findWordEnd(line, i + 1)
                        sb.append(line.substring(i, end))
                        spans.add(Triple(ANNOTATION, start, sb.length))
                        i = end
                    }
                    line[i].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit()) -> {
                        val end = findNumberEnd(line, i)
                        sb.append(line.substring(i, end))
                        spans.add(Triple(NUMBER, start, sb.length))
                        i = end
                    }
                    line[i].isLetter() || line[i] == '_' || line[i] == '$' -> {
                        val end = findWordEnd(line, i)
                        val word = line.substring(i, end)
                        sb.append(word)
                        when {
                            word in keywords -> spans.add(Triple(KEYWORD, start, sb.length))
                            word.firstOrNull()?.isUpperCase() == true ->
                                spans.add(Triple(TYPE, start, sb.length))
                            end < line.length && line[end] == '(' ->
                                spans.add(Triple(FUNCTION, start, sb.length))
                            else -> {}
                        }
                        i = end
                    }
                    else -> {
                        sb.append(line[i])
                        i++
                    }
                }
            }
        }
    }

    private fun highlightSql(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, code: String,
    ) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) sb.append("\n")
            var i = 0
            while (i < line.length) {
                val start = sb.length
                when {
                    line.startsWith("--", i) -> {
                        sb.append(line.substring(i))
                        spans.add(Triple(COMMENT, start, sb.length))
                        i = line.length
                    }
                    line[i] == '\'' || line[i] == '"' -> {
                        val end = findEndOfString(line, i, line[i])
                        sb.append(line.substring(i, end))
                        spans.add(Triple(STRING, start, sb.length))
                        i = end
                    }
                    line[i].isLetter() || line[i] == '_' -> {
                        val end = findWordEnd(line, i)
                        val word = line.substring(i, end)
                        sb.append(word)
                        if (word.uppercase() in sqlKeywords) {
                            spans.add(Triple(KEYWORD, start, sb.length))
                        }
                        i = end
                    }
                    line[i].isDigit() -> {
                        val end = findNumberEnd(line, i)
                        sb.append(line.substring(i, end))
                        spans.add(Triple(NUMBER, start, sb.length))
                        i = end
                    }
                    else -> { sb.append(line[i]); i++ }
                }
            }
        }
    }

    private fun highlightXml(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, code: String,
    ) {
        var i = 0
        while (i < code.length) {
            val start = sb.length
            when {
                code.startsWith("<!--", i) -> {
                    val end = code.indexOf("-->", i + 4)
                    val endIdx = if (end >= 0) end + 3 else code.length
                    sb.append(code.substring(i, endIdx))
                    spans.add(Triple(COMMENT, start, sb.length))
                    i = endIdx
                }
                code[i] == '<' -> {
                    val tagEnd = code.indexOf('>', i + 1)
                    if (tagEnd >= 0) {
                        val tag = code.substring(i + 1, tagEnd).trimStart()
                        if (tag.startsWith("/")) {
                            val nameEnd = findWordEnd(tag, 1)
                            sb.append("</")
                            sb.append(tag.substring(1, nameEnd))
                            sb.append(">")
                            spans.add(Triple(XML_TAG, start, sb.length))
                            i = tagEnd + 1
                        } else if (tag.endsWith("/")) {
                            val cleaned = tag.removeSuffix("/").trimEnd()
                            val spaceIdx = cleaned.indexOf(' ')
                            if (spaceIdx >= 0) {
                                val tagName = cleaned.substring(0, spaceIdx)
                                sb.append("<").append(tagName)
                                // attributes
                                val attrStart = sb.length
                                val attrPart = cleaned.substring(spaceIdx + 1)
                                appendXmlAttributes(sb, spans, attrPart)
                                sb.append("/>")
                                spans.add(Triple(XML_TAG, start, sb.length))
                                i = tagEnd + 1
                            } else {
                                sb.append(code.substring(i, tagEnd + 1))
                                spans.add(Triple(XML_TAG, start, sb.length))
                                i = tagEnd + 1
                            }
                        } else {
                            val spaceIdx = tag.indexOf(' ')
                            if (spaceIdx >= 0 && !tag.startsWith("?")) {
                                val tagName = tag.substring(0, spaceIdx)
                                sb.append("<").append(tagName)
                                val attrPart = tag.substring(spaceIdx + 1)
                                appendXmlAttributes(sb, spans, attrPart)
                                sb.append(">")
                                spans.add(Triple(XML_TAG, start, sb.length))
                                i = tagEnd + 1
                            } else {
                                sb.append(code.substring(i, tagEnd + 1))
                                spans.add(Triple(XML_TAG, start, sb.length))
                                i = tagEnd + 1
                            }
                        }
                    } else {
                        sb.append(code[i]); i++
                    }
                }
                code[i].isLetter() && i > 0 && code[i - 1] == '>' -> {
                    val textEnd = code.indexOf('<', i)
                    if (textEnd >= 0) {
                        sb.append(code.substring(i, textEnd))
                        i = textEnd
                    } else {
                        sb.append(code.substring(i))
                        i = code.length
                    }
                }
                else -> { sb.append(code[i]); i++ }
            }
        }
    }

    private fun appendXmlAttributes(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, attrText: String,
    ) {
        var j = 0
        while (j < attrText.length) {
            when {
                attrText[j] == ' ' || attrText[j] == '\t' || attrText[j] == '\n' -> {
                    sb.append(attrText[j]); j++
                }
                attrText[j].isLetter() || attrText[j] == '_' || attrText[j] == ':' -> {
                    val attrStart = sb.length
                    val end = findWordEnd(attrText, j)
                    sb.append(attrText.substring(j, end))
                    spans.add(Triple(XML_ATTR, attrStart, sb.length))
                    j = end
                }
                attrText[j] == '=' -> { sb.append(attrText[j]); j++ }
                attrText[j] == '"' || attrText[j] == '\'' -> {
                    val valStart = sb.length
                    val end = findEndOfString(attrText, j, attrText[j])
                    sb.append(attrText.substring(j, end))
                    spans.add(Triple(XML_VALUE, valStart, sb.length))
                    j = end
                }
                else -> { sb.append(attrText[j]); j++ }
            }
        }
    }

    private fun highlightJson(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, code: String,
    ) {
        var i = 0
        while (i < code.length) {
            val start = sb.length
            when {
                code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i)
                    val endIdx = if (end >= 0) end else code.length
                    sb.append(code.substring(i, endIdx))
                    spans.add(Triple(COMMENT, start, sb.length))
                    i = endIdx
                }
                code[i] == '"' -> {
                    val end = findEndOfString(code, i, '"')
                    val content = code.substring(i, end)
                    var k = end
                    while (k < code.length && (code[k] == ' ' || code[k] == '\t')) k++
                    if (k < code.length && code[k] == ':') {
                        sb.append(content)
                        spans.add(Triple(YAML_KEY, start, sb.length))
                    } else {
                        sb.append(content)
                        spans.add(Triple(STRING, start, sb.length))
                    }
                    i = end
                }
                code[i].isDigit() || code[i] == '-' -> {
                    val end = findNumberEnd(code, i)
                    sb.append(code.substring(i, end))
                    spans.add(Triple(NUMBER, start, sb.length))
                    i = end
                }
                code.startsWith("true", i) || code.startsWith("false", i) ||
                    code.startsWith("null", i) -> {
                    val end = findWordEnd(code, i)
                    sb.append(code.substring(i, end))
                    spans.add(Triple(KEYWORD, start, sb.length))
                    i = end
                }
                else -> { sb.append(code[i]); i++ }
            }
        }
    }

    private fun highlightCss(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, code: String,
    ) {
        var i = 0
        while (i < code.length) {
            val start = sb.length
            when {
                code.startsWith("/*", i) -> {
                    val end = code.indexOf("*/", i + 2)
                    val endIdx = if (end >= 0) end + 2 else code.length
                    sb.append(code.substring(i, endIdx))
                    spans.add(Triple(COMMENT, start, sb.length))
                    i = endIdx
                }
                code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i)
                    val endIdx = if (end >= 0) end else code.length
                    sb.append(code.substring(i, endIdx))
                    spans.add(Triple(COMMENT, start, sb.length))
                    i = endIdx
                }
                code[i] == '.' || code[i] == '#' || code[i] == '*' -> {
                    val end = findWordEnd(code, i)
                    sb.append(code.substring(i, end))
                    spans.add(Triple(TYPE, start, sb.length))
                    i = end
                }
                code[i].isLetter() || code[i] == '-' -> {
                    val end = findWordEnd(code, i, true)
                    var k = end
                    while (k < code.length && code[k] == ' ') k++
                    if (k < code.length && code[k] == ':') {
                        sb.append(code.substring(i, end))
                        spans.add(Triple(PROPERTY, start, sb.length))
                    } else {
                        sb.append(code.substring(i, end))
                    }
                    i = end
                }
                code[i] == '"' || code[i] == '\'' -> {
                    val end = findEndOfString(code, i, code[i])
                    sb.append(code.substring(i, end))
                    spans.add(Triple(STRING, start, sb.length))
                    i = end
                }
                code[i].isDigit() -> {
                    val end = findNumberEnd(code, i)
                    sb.append(code.substring(i, end))
                    spans.add(Triple(NUMBER, start, sb.length))
                    i = end
                }
                else -> { sb.append(code[i]); i++ }
            }
        }
    }

    private fun highlightYaml(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, code: String,
    ) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) sb.append("\n")
            if (line.isBlank()) continue
            if (line.trimStart().startsWith("#")) {
                val start = sb.length
                sb.append(line)
                spans.add(Triple(COMMENT, start, sb.length))
                continue
            }
            val colonIdx = line.indexOf(':')
            if (colonIdx >= 0) {
                val key = line.substring(0, colonIdx)
                val value = line.substring(colonIdx)
                val keyStart = sb.length
                sb.append(key)
                spans.add(Triple(YAML_KEY, keyStart, sb.length))
                if (value == ":") { sb.append(":"); continue }
                val trimmedValue = value.substring(1)
                val leadingSpaces = value.length - 1 - trimmedValue.length
                sb.append(":")
                if (leadingSpaces > 0) sb.append(" ".repeat(leadingSpaces))
                val valStart = sb.length
                when {
                    trimmedValue.startsWith("\"") || trimmedValue.startsWith("'") -> {
                        sb.append(trimmedValue)
                        spans.add(Triple(STRING, valStart, sb.length))
                    }
                    trimmedValue.trim() == "true" || trimmedValue.trim() == "false" ||
                        trimmedValue.trim() == "null" -> {
                        sb.append(trimmedValue)
                        spans.add(Triple(KEYWORD, valStart, sb.length))
                    }
                    trimmedValue.trim().isNotEmpty() &&
                        (trimmedValue.trim()[0].isDigit() || trimmedValue.trim() == "-") -> {
                        sb.append(trimmedValue)
                        spans.add(Triple(NUMBER, valStart, sb.length))
                    }
                    else -> sb.append(trimmedValue)
                }
            } else {
                sb.append(line)
            }
        }
    }

    private fun highlightProperties(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, code: String,
    ) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) sb.append("\n")
            if (line.isBlank()) continue
            if (line.trimStart().startsWith("#") || line.trimStart().startsWith("!")) {
                val start = sb.length; sb.append(line); spans.add(Triple(COMMENT, start, sb.length))
                continue
            }
            val eqIdx = line.indexOf('=')
            val colonIdx = line.indexOf(':')
            val sepIdx = when {
                eqIdx >= 0 && colonIdx >= 0 -> minOf(eqIdx, colonIdx)
                eqIdx >= 0 -> eqIdx; colonIdx >= 0 -> colonIdx; else -> -1
            }
            if (sepIdx >= 0) {
                val keyStart = sb.length; sb.append(line.substring(0, sepIdx))
                spans.add(Triple(YAML_KEY, keyStart, sb.length))
                sb.append(line[sepIdx])
                val valStart = sb.length; sb.append(line.substring(sepIdx + 1))
                spans.add(Triple(STRING, valStart, sb.length))
            } else {
                sb.append(line)
            }
        }
    }

    private fun highlightDiff(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, code: String,
    ) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) sb.append("\n")
            val start = sb.length; sb.append(line)
            when {
                line.startsWith("+++") || line.startsWith("---") ->
                    spans.add(Triple(TYPE, start, sb.length))
                line.startsWith("@@") ->
                    spans.add(Triple(KEYWORD, start, sb.length))
                line.startsWith("+") ->
                    spans.add(Triple(STRING, start, sb.length))
                line.startsWith("-") ->
                    spans.add(Triple(ANNOTATION, start, sb.length))
            }
        }
    }

    private fun highlightFallback(
        sb: StringBuilder, spans: MutableList<Triple<SpanStyle, Int, Int>>, code: String,
    ) {
        var i = 0
        while (i < code.length) {
            val start = sb.length
            when {
                code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i)
                    val endIdx = if (end >= 0) end else code.length
                    sb.append(code.substring(i, endIdx))
                    spans.add(Triple(COMMENT, start, sb.length))
                    i = endIdx
                }
                code.startsWith("/*", i) -> {
                    val end = code.indexOf("*/", i + 2)
                    val endIdx = if (end >= 0) end + 2 else code.length
                    sb.append(code.substring(i, endIdx))
                    spans.add(Triple(COMMENT, start, sb.length))
                    i = endIdx
                }
                code[i] == '"' -> {
                    val end = findEndOfString(code, i, '"')
                    sb.append(code.substring(i, end))
                    spans.add(Triple(STRING, start, sb.length))
                    i = end
                }
                code[i] == '\'' -> {
                    val end = findEndOfString(code, i, '\'')
                    sb.append(code.substring(i, end))
                    spans.add(Triple(STRING, start, sb.length))
                    i = end
                }
                code[i].isDigit() -> {
                    val end = findNumberEnd(code, i)
                    sb.append(code.substring(i, end))
                    spans.add(Triple(NUMBER, start, sb.length))
                    i = end
                }
                else -> { sb.append(code[i]); i++ }
            }
        }
    }

    private fun findWordEnd(text: String, start: Int, includeHyphen: Boolean = false): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' ||
                text[i] == '$' || text[i] == '-' || text[i] == '.')
        ) {
            if (text[i] == '-' && !includeHyphen) break
            if (text[i] == '.' && (i + 1 >= text.length || !text[i + 1].isLetterOrDigit())) break
            i++
        }
        return i
    }

    private fun findNumberEnd(text: String, start: Int): Int {
        var i = start
        var hasDot = false
        var hasHex = false
        if (i < text.length && text[i] == '-') i++
        if (i + 1 < text.length && text[i] == '0' && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
            hasHex = true; i += 2
        }
        while (i < text.length) {
            when {
                text[i] in '0'..'9' -> i++
                text[i] == '.' && !hasDot && !hasHex -> { hasDot = true; i++ }
                text[i] == 'f' || text[i] == 'F' || text[i] == 'L' || text[i] == 'l' ||
                    text[i] == 'D' || text[i] == 'd' -> { i++; break }
                text[i] in 'a'..'f' || text[i] in 'A'..'F' -> if (hasHex) i++ else break
                text[i] == '_' -> i++
                else -> break
            }
        }
        return i
    }

    private fun findEndOfString(text: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == quote -> return i + 1
                else -> i++
            }
        }
        return text.length
    }

    fun getBackgroundColor(): Color = Color(0xFF1E1E1E)
    fun getDefaultLineColor(): Color = Color(0xFFA9B7C6)

    private val kotlinKeywords = setOf(
        "val", "var", "fun", "class", "object", "interface", "enum", "sealed",
        "data", "abstract", "open", "override", "private", "protected", "public",
        "internal", "if", "else", "when", "for", "while", "do", "try", "catch",
        "finally", "throw", "return", "break", "continue", "import", "package",
        "as", "in", "is", "!is", "as?", "typeof", "super", "this", "null",
        "true", "false", "companion", "init", "constructor", "by", "delegate",
        "get", "set", "field", "value", "suspend", "inline", "infix", "tailrec",
        "operator", "vararg", "reified", "crossinline", "noinline",
        "actual", "expect", "typealias", "annotation", "inner",
    )

    private val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for",
        "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "true", "false", "null",
        "var", "record", "sealed", "permits", "yield",
    )

    private val pythonKeywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield",
    )

    private val jsKeywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "default", "delete", "do", "else", "enum",
        "export", "extends", "false", "finally", "for", "function", "if",
        "import", "in", "instanceof", "let", "new", "null", "of",
        "return", "super", "switch", "this", "throw", "true", "try",
        "typeof", "undefined", "var", "void", "while", "with", "yield",
    )

    private val goKeywords = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer",
        "else", "fallthrough", "for", "func", "go", "goto", "if",
        "import", "interface", "map", "package", "range", "return",
        "select", "struct", "switch", "type", "var", "true", "false", "nil",
    )

    private val rustKeywords = setOf(
        "as", "async", "await", "break", "const", "continue", "crate", "else",
        "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let",
        "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
        "self", "Self", "static", "struct", "super", "trait", "true",
        "type", "unsafe", "use", "where", "while", "yield",
    )

    private val shellKeywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done",
        "case", "esac", "in", "function", "return", "exit", "export",
        "local", "source", "set", "unset", "declare", "typeset",
    )

    private val sqlKeywords = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE",
        "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX",
        "VIEW", "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN", "IS",
        "NULL", "AS", "ON", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER",
        "FULL", "CROSS", "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC",
        "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "COUNT", "SUM",
        "AVG", "MIN", "MAX", "EXISTS", "CASE", "WHEN", "THEN", "ELSE",
        "END", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CASCADE",
        "INT", "VARCHAR", "TEXT", "BOOLEAN", "DATE", "TIMESTAMP", "FLOAT",
        "DOUBLE", "DECIMAL", "BIGINT", "SMALLINT", "CHAR", "BLOB",
    )

    private val gradleKeywords = setOf(
        "apply", "plugin", "dependencies", "repositories", "android",
        "defaultConfig", "buildTypes", "compileSdk", "minSdk", "targetSdk",
        "versionCode", "versionName", "implementation", "api", "compileOnly",
        "runtimeOnly", "testImplementation", "androidTestImplementation",
        "debugImplementation", "release", "debug", "buildFeatures",
        "compose", "viewBinding", "dataBinding", "namespace",
        "compileSdkVersion", "buildToolsVersion", "sourceSets",
        "lintOptions", "packagingOptions", "signingConfigs",
        "flavorDimensions", "productFlavors",
    )
}
