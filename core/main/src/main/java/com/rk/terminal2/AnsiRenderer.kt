package com.rk.terminal2

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import java.util.ArrayDeque

data class AnsiStyle(
    val foreground: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val blink: Boolean = false,
    val dim: Boolean = false,
    val inverse: Boolean = false,
)

object AnsiColorParser {
    private val ANSI_SEQUENCE = Regex("\u001B\\[[;\\d;]*[A-Za-z]")
    private val SGR_REGEX = Regex("\u001B\\[([\\d;]*)m")

    private val ansiColors = mapOf(
        30 to 0xFF000000.toInt(), 31 to 0xFFCC0000.toInt(), 32 to 0xFF4E9A06.toInt(),
        33 to 0xFFC4A000.toInt(), 34 to 0xFF3465A4.toInt(), 35 to 0xFF75507B.toInt(),
        36 to 0xFF06989A.toInt(), 37 to 0xFFD3D7CF.toInt(),
        90 to 0xFF555753.toInt(), 91 to 0xFFEF2929.toInt(), 92 to 0xFF8AE234.toInt(),
        93 to 0xFFFCE94F.toInt(), 94 to 0xFF729FCF.toInt(), 95 to 0xFFAD7FA8.toInt(),
        96 to 0xFF34E2E2.toInt(), 97 to 0xFFEEEEEC.toInt(),
    )

    private val brightAnsiColors = ansiColors.mapKeys { (k, v) ->
        if (k in 30..37) k + 60 else k
    }

    fun stripAnsi(text: String): String = text.replace(ANSI_SEQUENCE, "")

    fun parseAndRender(text: String, defaultForeground: Int = 0xFFFFFFFF.toInt(), defaultBackground: Int = 0xFF000000.toInt()): SpannableStringBuilder {
        val result = SpannableStringBuilder()
        val styleStack = ArrayDeque<AnsiStyle>()
        styleStack.push(AnsiStyle())

        var currentStyle = AnsiStyle()
        var lastEnd = 0

        SGR_REGEX.findAll(text).forEach { match ->
            if (match.range.first > lastEnd) {
                result.append(text.substring(lastEnd, match.range.first))
            }
            val params = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
            currentStyle = applySgrParams(currentStyle, params)
            lastEnd = match.range.last + 1
        }

        if (lastEnd < text.length) {
            result.append(text.substring(lastEnd))
        }

        return applySpans(result, currentStyle, defaultForeground, defaultBackground)
    }

    private fun applySgrParams(style: AnsiStyle, params: List<Int>): AnsiStyle {
        var fg = style.foreground
        var bg = style.background
        var bold = style.bold
        var italic = style.italic
        var underline = style.underline
        var strikethrough = style.strikethrough
        var blink = style.blink
        var dim = style.dim
        var inverse = style.inverse

        var i = 0
        while (i < params.size) {
            val code = params[i]
            when {
                code == 0 -> {
                    fg = null; bg = null; bold = false; italic = false; underline = false; strikethrough = false; blink = false; dim = false; inverse = false
                }
                code == 1 -> bold = true
                code == 2 -> dim = true
                code == 3 -> italic = true
                code == 4 -> underline = true
                code == 7 -> inverse = true
                code == 9 -> strikethrough = true
                code == 22 -> { bold = false; dim = false }
                code == 23 -> italic = false
                code == 24 -> underline = false
                code == 27 -> inverse = false
                code == 29 -> strikethrough = false
                code in 30..37 -> fg = ansiColors[code]
                code in 90..97 -> fg = brightAnsiColors[code]
                code in 40..47 -> bg = ansiColors[code - 10]
                code in 100..107 -> bg = brightAnsiColors[code - 60]
                code == 38 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        fg = parse256Color(params[i + 2]); i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        fg = parseTrueColor(params[i + 2], params[i + 3], params[i + 4]); i += 4
                    }
                }
                code == 48 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        bg = parse256Color(params[i + 2]); i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        bg = parseTrueColor(params[i + 2], params[i + 3], params[i + 4]); i += 4
                    }
                }
            }
            i++
        }

        return AnsiStyle(fg, bg, bold, italic, underline, strikethrough, blink, dim, inverse)
    }

    private fun parse256Color(code: Int): Int {
        if (code < 16) {
            return when (code) {
                0 -> 0xFF000000.toInt(); 1 -> 0xFFCC0000.toInt(); 2 -> 0xFF4E9A06.toInt()
                3 -> 0xFFC4A000.toInt(); 4 -> 0xFF3465A4.toInt(); 5 -> 0xFF75507B.toInt()
                6 -> 0xFF06989A.toInt(); 7 -> 0xFFD3D7CF.toInt(); 8 -> 0xFF555753.toInt()
                9 -> 0xFFEF2929.toInt(); 10 -> 0xFF8AE234.toInt(); 11 -> 0xFFFCE94F.toInt()
                12 -> 0xFF729FCF.toInt(); 13 -> 0xFFAD7FA8.toInt(); 14 -> 0xFF34E2E2.toInt()
                15 -> 0xFFEEEEEC.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
        }
        if (code < 232) {
            val c = code - 16
            val r = (c / 36) * 51
            val g = ((c % 36) / 6) * 51
            val b = (c % 6) * 51
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val gray = (code - 232) * 10 + 8
        return 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
    }

    private fun parseTrueColor(r: Int, g: Int, b: Int): Int =
        0xFF000000.toInt() or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun applySpans(text: SpannableStringBuilder, style: AnsiStyle, defaultFg: Int, defaultBg: Int): SpannableStringBuilder {
        val length = text.length
        if (length == 0) return text

        val fg = if (style.inverse) (style.background ?: defaultBg) else (style.foreground ?: defaultFg)
        val bg = if (style.inverse) (style.foreground ?: defaultFg) else (style.background ?: defaultBg)

        text.setSpan(ForegroundColorSpan(fg), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bg != defaultBg || style.inverse) {
            text.setSpan(BackgroundColorSpan(bg), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.bold) {
            text.setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.italic) {
            text.setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.underline) {
            text.setSpan(UnderlineSpan(), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }
}
