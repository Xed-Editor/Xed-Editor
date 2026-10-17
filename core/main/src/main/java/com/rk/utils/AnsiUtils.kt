package com.rk.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.termux.terminal.TerminalColorScheme
import com.termux.terminal.TextStyle
import java.util.Properties

/** One escape sequence: CSI (group 1 = params, group 2 = final byte), OSC, or a two-character escape. */
private val ANSI_ESCAPE =
    Regex("\u001B(?:\\[([0-?]*)[ -/]*([@-~])|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[@-Z\\\\^_])")

private data class AnsiState(
    val fg: Color = Color.Unspecified,
    val bg: Color = Color.Unspecified,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val reverse: Boolean = false,
)

private class AnsiTheme(val palette: List<Color>, val defaultFg: Color, val defaultBg: Color)

@Volatile private var themeCache: Pair<Properties, AnsiTheme>? = null

private fun resolveTheme(props: Properties): AnsiTheme {
    themeCache?.let { if (it.first === props) return it.second }

    val scheme = TerminalColorScheme()
    runCatching { scheme.updateWith(props) }
    val colors = scheme.mDefaultColors
    return AnsiTheme(
            palette = List(256) { Color(colors[it]) },
            defaultFg = Color(colors[TextStyle.COLOR_INDEX_FOREGROUND]),
            defaultBg = Color(colors[TextStyle.COLOR_INDEX_BACKGROUND]),
        )
        .also { themeCache = props to it }
}

private fun channel(v: Int) = v.coerceIn(0, 255)

private fun AnsiState.applySgr(params: List<Int>, palette: List<Color>): AnsiState {
    var s = this
    var i = 0
    while (i < params.size) {
        val code = params[i]
        s =
            when (code) {
                0 -> AnsiState()
                1 -> s.copy(bold = true)
                2 -> s.copy(dim = true)
                3 -> s.copy(italic = true)
                4 -> s.copy(underline = true)
                7 -> s.copy(reverse = true)
                9 -> s.copy(strike = true)
                22 -> s.copy(bold = false, dim = false)
                23 -> s.copy(italic = false)
                24 -> s.copy(underline = false)
                27 -> s.copy(reverse = false)
                29 -> s.copy(strike = false)
                in 30..37 -> s.copy(fg = palette[code - 30])
                39 -> s.copy(fg = Color.Unspecified)
                in 40..47 -> s.copy(bg = palette[code - 40])
                49 -> s.copy(bg = Color.Unspecified)
                in 90..97 -> s.copy(fg = palette[code - 90 + 8])
                in 100..107 -> s.copy(bg = palette[code - 100 + 8])
                38,
                48 -> {
                    // 38;5;n (256 colors) or 38;2;r;g;b (truecolor)
                    val mode = params.getOrNull(i + 1)
                    val color =
                        when (mode) {
                            5 if i + 2 < params.size -> palette[channel(params[i + 2])].also { i += 2 }
                            2 if i + 4 < params.size ->
                                Color(channel(params[i + 2]), channel(params[i + 3]), channel(params[i + 4])).also {
                                    i += 4
                                }

                            else -> null
                        }
                    when {
                        color == null -> s
                        code == 38 -> s.copy(fg = color)
                        else -> s.copy(bg = color)
                    }
                }
                else -> s
            }
        i++
    }
    return s
}

private fun AnsiState.toSpanStyle(defaultFg: Color, defaultBg: Color): SpanStyle? {
    var fg = this.fg
    var bg = this.bg
    if (reverse) {
        fg = this.bg.takeOrElse { defaultBg }
        bg = this.fg.takeOrElse { defaultFg }
    }
    if (dim) fg = fg.takeOrElse { defaultFg }.copy(alpha = 0.6f)

    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strike) add(TextDecoration.LineThrough)
    }
    val style =
        SpanStyle(
            color = fg,
            background = bg,
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        )
    return if (style == SpanStyle()) null else style
}

/** Removes all ANSI escape sequences from [text]. */
fun stripAnsi(text: String): String = if ('\u001B' !in text) text else ANSI_ESCAPE.replace(text, "")

/** Parses ANSI SGR escape sequences into an AnnotatedString using the given terminal theme. */
fun parseAnsiToAnnotatedString(text: String, props: Properties): AnnotatedString {
    if ('\u001B' !in text) return AnnotatedString(text)

    val theme = resolveTheme(props)

    return buildAnnotatedString {
        var state = AnsiState()
        var last = 0

        fun appendChunk(chunk: String) {
            if (chunk.isEmpty()) return
            val style = state.toSpanStyle(theme.defaultFg, theme.defaultBg)
            if (style != null) withStyle(style) { append(chunk) } else append(chunk)
        }

        for (match in ANSI_ESCAPE.findAll(text)) {
            appendChunk(text.substring(last, match.range.first))
            last = match.range.last + 1

            // Only plain SGR ("m" with digits and ';') styles text. Colon forms and all other sequences are dropped.
            val params = match.groupValues[1]
            if (match.groupValues[2] == "m" && params.all { it in '0'..'9' || it == ';' }) {
                // "" splits to [""] -> [0], so "ESC[m" is a reset and empty params count as 0
                state = state.applySgr(params.split(';').map { it.toIntOrNull() ?: 0 }, theme.palette)
            }
        }
        appendChunk(text.substring(last))
    }
}
