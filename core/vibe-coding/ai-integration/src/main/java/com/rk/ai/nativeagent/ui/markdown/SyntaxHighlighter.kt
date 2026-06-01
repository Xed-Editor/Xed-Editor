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
    private val BUILT_IN = SpanStyle(color = Color(0xFFD0D0D0))
    private val DEFAULT = SpanStyle(color = Color(0xFFA9B7C6))
    private val VARIABLE = SpanStyle(color = Color(0xFF9876AA))
    private val XML_TAG = SpanStyle(color = Color(0xFFCC7832))
    private val XML_ATTR = SpanStyle(color = Color(0xFF6C9EF8))
    private val XML_VALUE = SpanStyle(color = Color(0xFF6A8759))
    private val YAML_KEY = SpanStyle(color = Color(0xFF6C9EF8))
    private val YAML_VALUE = SpanStyle(color = Color(0xFFA9B7C6))

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
        "sealed", "value class", "data class",
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

    fun highlight(code: String, language: String?): AnnotatedString = buildAnnotatedString {
        when {
            language in listOf("kotlin", "kts", "kt") -> applyLanguage(code, kotlinKeywords, true)
            language in listOf("java") -> applyLanguage(code, javaKeywords, true)
            language in listOf("python", "py") -> applyLanguage(code, pythonKeywords, false)
            language in listOf("javascript", "js", "typescript", "ts", "jsx", "tsx") ->
                applyLanguage(code, jsKeywords, false)
            language in listOf("go") -> applyLanguage(code, goKeywords, false)
            language in listOf("rust", "rs") -> applyLanguage(code, rustKeywords, false)
            language in listOf("shell", "bash", "sh", "zsh") -> applyLanguage(code, shellKeywords, false)
            language in listOf("sql") -> applySql(code)
            language in listOf("xml", "html", "svg") -> applyXml(code)
            language in listOf("json") -> applyJson(code)
            language in listOf("css") -> applyCss(code)
            language in listOf("yaml", "yml") -> applyYaml(code)
            language in listOf("gradle", "groovy") -> applyLanguage(code, gradleKeywords, false)
            language in listOf("properties", "conf") -> applyProperties(code)
            language in listOf("diff", "patch") -> applyDiff(code)
            else -> applyFallback(code)
        }
    }

    private fun AnnotatedString.Builder.applyLanguage(
        code: String, keywords: Set<String>, hasAnnotations: Boolean,
    ) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) append("\n")
            var i = 0
            while (i < line.length) {
                when {
                    // Single-line comment
                    line.startsWith("//", i) -> {
                        withStyle(COMMENT) { append(line.substring(i)) }
                        i = line.length
                    }
                    // Block comment start
                    line.startsWith("/*", i) -> {
                        val end = line.indexOf("*/", i + 2)
                        if (end >= 0) {
                            withStyle(COMMENT) { append(line.substring(i, end + 2)) }
                            i = end + 2
                        } else {
                            withStyle(COMMENT) { append(line.substring(i)) }
                            i = line.length
                        }
                    }
                    // String (double quote)
                    line[i] == '"' -> {
                        val end = findEndOfString(line, i, '"')
                        withStyle(STRING) { append(line.substring(i, end)) }
                        i = end
                    }
                    // String (single quote)
                    line[i] == '\'' -> {
                        val end = findEndOfString(line, i, '\'')
                        withStyle(STRING) { append(line.substring(i, end)) }
                        i = end
                    }
                    // Template string
                    line[i] == '`' -> {
                        val end = line.indexOf('`', i + 1)
                        val endIdx = if (end >= 0) end + 1 else line.length
                        withStyle(STRING) { append(line.substring(i, endIdx)) }
                        i = endIdx
                    }
                    // Annotations (@Annotation)
                    line[i] == '@' && hasAnnotations -> {
                        val end = findWordEnd(line, i + 1)
                        withStyle(ANNOTATION) { append(line.substring(i, end)) }
                        i = end
                    }
                    // Numbers
                    line[i].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit()) -> {
                        val end = findNumberEnd(line, i)
                        withStyle(NUMBER) { append(line.substring(i, end)) }
                        i = end
                    }
                    // Identifiers / Keywords
                    line[i].isLetter() || line[i] == '_' || line[i] == '$' -> {
                        val end = findWordEnd(line, i)
                        val word = line.substring(i, end)
                        if (word in keywords) {
                            withStyle(KEYWORD) { append(word) }
                        } else if (word.firstOrNull()?.isUpperCase() == true ||
                            word.startsWith("I") && word.length == 1
                        ) {
                            withStyle(TYPE) { append(word) }
                        } else {
                            withStyle(FUNCTION) {
                                if (end < line.length && line[end] == '(') append(word)
                                else { withStyle(DEFAULT) { append(word) } }
                            }
                        }
                        i = end
                    }
                    else -> {
                        withStyle(DEFAULT) { append(line[i]) }
                        i++
                    }
                }
            }
        }
    }

    private fun AnnotatedString.Builder.applySql(code: String) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) append("\n")
            var i = 0
            while (i < line.length) {
                when {
                    line.startsWith("--", i) -> {
                        withStyle(COMMENT) { append(line.substring(i)) }
                        i = line.length
                    }
                    line[i] == '\'' || line[i] == '"' -> {
                        val end = findEndOfString(line, i, line[i])
                        withStyle(STRING) { append(line.substring(i, end)) }
                        i = end
                    }
                    line[i].isLetter() || line[i] == '_' -> {
                        val end = findWordEnd(line, i)
                        val word = line.substring(i, end)
                        if (word.uppercase() in sqlKeywords) {
                            withStyle(KEYWORD) { append(word) }
                        } else {
                            withStyle(DEFAULT) { append(word) }
                        }
                        i = end
                    }
                    line[i].isDigit() -> {
                        val end = findNumberEnd(line, i)
                        withStyle(NUMBER) { append(line.substring(i, end)) }
                        i = end
                    }
                    else -> {
                        withStyle(DEFAULT) { append(line[i]) }
                        i++
                    }
                }
            }
        }
    }

    private fun AnnotatedString.Builder.applyXml(code: String) {
        var i = 0
        while (i < code.length) {
            when {
                code.startsWith("<!--", i) -> {
                    val end = code.indexOf("-->", i + 4)
                    val endIdx = if (end >= 0) end + 3 else code.length
                    withStyle(COMMENT) { append(code.substring(i, endIdx)) }
                    i = endIdx
                }
                code[i] == '<' -> {
                    val tagEnd = code.indexOf('>', i + 1)
                    if (tagEnd >= 0) {
                        val tag = code.substring(i + 1, tagEnd).trimStart()
                        if (tag.startsWith("/")) {
                            withStyle(XML_TAG) { append("</") }
                            val nameEnd = findWordEnd(tag, 1)
                            withStyle(XML_TAG) { append(tag.substring(1, nameEnd)) }
                            withStyle(XML_TAG) { append(">") }
                            i = tagEnd + 1
                        } else if (tag.endsWith("/")) {
                            val cleaned = tag.removeSuffix("/").trimEnd()
                            val spaceIdx = cleaned.indexOf(' ')
                            if (spaceIdx >= 0) {
                                val tagName = cleaned.substring(0, spaceIdx)
                                withStyle(XML_TAG) { append("<$tagName") }
                                i = i + 1 + tagName.length + 1
                                val attrPart = cleaned.substring(spaceIdx + 1)
                                appendAttributes(attrPart)
                                withStyle(XML_TAG) { append("/>") }
                                i = tagEnd + 1
                            } else {
                                withStyle(XML_TAG) { append(code.substring(i, tagEnd + 1)) }
                                i = tagEnd + 1
                            }
                        } else {
                            val spaceIdx = tag.indexOf(' ')
                            if (spaceIdx >= 0 && !tag.startsWith("?")) {
                                val tagName = tag.substring(0, spaceIdx)
                                withStyle(XML_TAG) { append("<$tagName") }
                                i = i + 1 + tagName.length + 1
                                val attrPart = tag.substring(spaceIdx + 1)
                                appendAttributes(attrPart)
                                withStyle(XML_TAG) { append(">") }
                                i = tagEnd + 1
                            } else {
                                withStyle(XML_TAG) { append(code.substring(i, tagEnd + 1)) }
                                i = tagEnd + 1
                            }
                        }
                    } else {
                        withStyle(DEFAULT) { append(code[i]) }
                        i++
                    }
                }
                code[i].isLetter() && i > 0 && code[i - 1] == '>' -> {
                    val textEnd = code.indexOf('<', i)
                    if (textEnd >= 0) {
                        withStyle(DEFAULT) { append(code.substring(i, textEnd)) }
                        i = textEnd
                    } else {
                        withStyle(DEFAULT) { append(code.substring(i)) }
                        i = code.length
                    }
                }
                else -> {
                    withStyle(DEFAULT) { append(code[i]) }
                    i++
                }
            }
        }
    }

    private fun AnnotatedString.Builder.appendAttributes(attrText: String) {
        var j = 0
        while (j < attrText.length) {
            when {
                attrText[j] == ' ' || attrText[j] == '\t' || attrText[j] == '\n' -> {
                    append(attrText[j]); j++
                }
                attrText[j].isLetter() || attrText[j] == '_' || attrText[j] == ':' -> {
                    val end = findWordEnd(attrText, j)
                    val name = attrText.substring(j, end)
                    withStyle(XML_ATTR) { append(name) }
                    j = end
                }
                attrText[j] == '=' -> {
                    append(attrText[j]); j++
                }
                attrText[j] == '"' || attrText[j] == '\'' -> {
                    val end = findEndOfString(attrText, j, attrText[j])
                    withStyle(XML_VALUE) { append(attrText.substring(j, end)) }
                    j = end
                }
                else -> { append(attrText[j]); j++ }
            }
        }
    }

    private fun AnnotatedString.Builder.applyJson(code: String) {
        var i = 0
        while (i < code.length) {
            when {
                code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i); val endIdx = if (end >= 0) end else code.length
                    withStyle(COMMENT) { append(code.substring(i, endIdx)) }; i = endIdx
                }
                code[i] == '"' -> {
                    val end = findEndOfString(code, i, '"')
                    val content = code.substring(i, end)
                    val before = if (i > 0) code[i - 1] else ' '
                    if (before == ':' || before == ' ') {
                        // Check if it's a key (followed by ':')
                        val afterIdx = end
                        var k = afterIdx
                        while (k < code.length && (code[k] == ' ' || code[k] == '\t')) k++
                        if (k < code.length && code[k] == ':') {
                            withStyle(YAML_KEY) { append(content) }; i = end; continue
                        }
                    }
                    withStyle(STRING) { append(content) }; i = end
                }
                code[i].isDigit() || code[i] == '-' -> {
                    if (code[i] == '-' || code[i].isDigit()) {
                        val end = findNumberEnd(code, i)
                        withStyle(NUMBER) { append(code.substring(i, end)) }; i = end
                    } else { append(code[i]); i++ }
                }
                code.startsWith("true", i) || code.startsWith("false", i) ||
                    code.startsWith("null", i) -> {
                    val end = findWordEnd(code, i)
                    withStyle(KEYWORD) { append(code.substring(i, end)) }; i = end
                }
                else -> { append(code[i]); i++ }
            }
        }
    }

    private fun AnnotatedString.Builder.applyCss(code: String) {
        var i = 0
        while (i < code.length) {
            when {
                code.startsWith("/*", i) -> {
                    val end = code.indexOf("*/", i + 2)
                    val endIdx = if (end >= 0) end + 2 else code.length
                    withStyle(COMMENT) { append(code.substring(i, endIdx)) }; i = endIdx
                }
                code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i); val endIdx = if (end >= 0) end else code.length
                    withStyle(COMMENT) { append(code.substring(i, endIdx)) }; i = endIdx
                }
                code[i] == '.' || code[i] == '#' || code[i] == '*' -> {
                    val end = findWordEnd(code, i)
                    withStyle(TYPE) { append(code.substring(i, end)) }; i = end
                }
                code[i].isLetter() || code[i] == '-' -> {
                    val end = findWordEnd(code, i, true)
                    val wordEnd = end
                    var k = end
                    while (k < code.length && code[k] == ' ') k++
                    if (k < code.length && code[k] == ':') {
                        withStyle(PROPERTY) { append(code.substring(i, wordEnd)) }; i = wordEnd
                    } else {
                        withStyle(DEFAULT) { append(code.substring(i, wordEnd)) }; i = wordEnd
                    }
                }
                code[i] == '"' || code[i] == '\'' -> {
                    val end = findEndOfString(code, i, code[i])
                    withStyle(STRING) { append(code.substring(i, end)) }; i = end
                }
                code[i].isDigit() -> {
                    val end = findNumberEnd(code, i)
                    withStyle(NUMBER) { append(code.substring(i, end)) }; i = end
                }
                else -> { append(code[i]); i++ }
            }
        }
    }

    private fun AnnotatedString.Builder.applyYaml(code: String) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) append("\n")
            if (line.isBlank()) continue
            if (line.trimStart().startsWith("#")) {
                withStyle(COMMENT) { append(line) }; continue
            }
            val colonIdx = line.indexOf(':')
            if (colonIdx >= 0) {
                val key = line.substring(0, colonIdx)
                val value = line.substring(colonIdx)
                withStyle(YAML_KEY) { append(key) }
                if (value == ":") { append(":"); continue }
                val valueStr = value.substring(1).trimStart()
                val trimmedValue = value.substring(1)
                val leadingSpaces = value.length - 1 - trimmedValue.length
                append(":")
                if (leadingSpaces > 0) append(" ".repeat(leadingSpaces))
                when {
                    valueStr.startsWith("\"") || valueStr.startsWith("'") -> {
                        withStyle(STRING) { append(trimmedValue) }
                    }
                    valueStr == "true" || valueStr == "false" || valueStr == "null" -> {
                        withStyle(KEYWORD) { append(trimmedValue) }
                    }
                    valueStr.isNotEmpty() && (
                        valueStr[0].isDigit() || valueStr == "-" || valueStr == "+"
                    ) -> {
                        withStyle(NUMBER) { append(trimmedValue) }
                    }
                    else -> withStyle(YAML_VALUE) { append(trimmedValue) }
                }
            } else {
                withStyle(DEFAULT) { append(line) }
            }
        }
    }

    private fun AnnotatedString.Builder.applyProperties(code: String) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) append("\n")
            if (line.isBlank()) continue
            if (line.trimStart().startsWith("#") || line.trimStart().startsWith("!")) {
                withStyle(COMMENT) { append(line) }; continue
            }
            val eqIdx = line.indexOf('=')
            val colonIdx = line.indexOf(':')
            val sepIdx = when {
                eqIdx >= 0 && colonIdx >= 0 -> minOf(eqIdx, colonIdx)
                eqIdx >= 0 -> eqIdx; colonIdx >= 0 -> colonIdx; else -> -1
            }
            if (sepIdx >= 0) {
                withStyle(YAML_KEY) { append(line.substring(0, sepIdx)) }
                append(line[sepIdx])
                withStyle(STRING) { append(line.substring(sepIdx + 1)) }
            } else {
                withStyle(DEFAULT) { append(line) }
            }
        }
    }

    private fun AnnotatedString.Builder.applyDiff(code: String) {
        val lines = code.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) append("\n")
            when {
                line.startsWith("+++") || line.startsWith("---") -> {
                    withStyle(TYPE) { append(line) }
                }
                line.startsWith("@@") -> {
                    withStyle(KEYWORD) { append(line) }
                }
                line.startsWith("+") -> {
                    withStyle(STRING) { append(line) }
                }
                line.startsWith("-") -> {
                    withStyle(ANNOTATION) { append(line) }
                }
                else -> {
                    withStyle(DEFAULT) { append(line) }
                }
            }
        }
    }

    private fun applyFallback(code: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            when {
                code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i); val endIdx = if (end >= 0) end else code.length
                    withStyle(COMMENT) { append(code.substring(i, endIdx)) }; i = endIdx
                }
                code.startsWith("/*", i) -> {
                    val end = code.indexOf("*/", i + 2)
                    val endIdx = if (end >= 0) end + 2 else code.length
                    withStyle(COMMENT) { append(code.substring(i, endIdx)) }; i = endIdx
                }
                code[i] == '"' -> {
                    val end = findEndOfString(code, i, '"')
                    withStyle(STRING) { append(code.substring(i, end)) }; i = end
                }
                code[i] == '\'' -> {
                    val end = findEndOfString(code, i, '\'')
                    withStyle(STRING) { append(code.substring(i, end)) }; i = end
                }
                code[i].isDigit() -> {
                    val end = findNumberEnd(code, i)
                    withStyle(NUMBER) { append(code.substring(i, end)) }; i = end
                }
                else -> { append(code[i]); i++ }
            }
        }
    }

    private fun findWordEnd(text: String, start: Int, includeHyphen: Boolean = false): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' ||
                text[i] == '$' || text[i] == '-' || text[i] == '.' && i > start)
        ) {
            if (text[i] == '-' && !includeHyphen) break
            if (text[i] == '.' && i > start && (
                    i + 1 >= text.length || !text[i + 1].isLetterOrDigit())
            ) break
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
}
