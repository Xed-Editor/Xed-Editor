package com.rk.ai.nativeagent.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DiffHunk(
    val header: String,
    val lines: List<DiffLine>,
)

data class DiffLine(
    val content: String,
    val type: DiffLineType,
)

enum class DiffLineType {
    ADDED, REMOVED, CONTEXT, HEADER
}

object DiffParser {
    fun parse(diffText: String): List<DiffHunk> {
        val hunks = mutableListOf<DiffHunk>()
        val currentLines = mutableListOf<DiffLine>()
        var currentHeader = ""

        for (line in diffText.split("\n")) {
            when {
                line.startsWith("@@") -> {
                    if (currentLines.isNotEmpty()) {
                        hunks.add(DiffHunk(currentHeader, currentLines.toList()))
                        currentLines.clear()
                    }
                    currentHeader = line
                }
                line.startsWith("+") && !line.startsWith("+++") ->
                    currentLines.add(DiffLine(line, DiffLineType.ADDED))
                line.startsWith("-") && !line.startsWith("---") ->
                    currentLines.add(DiffLine(line, DiffLineType.REMOVED))
                line.startsWith("---") || line.startsWith("+++") ||
                    line.startsWith("diff --git") || line.startsWith("index") -> {
                    // Skip file headers, handled via header detection
                }
                else -> currentLines.add(DiffLine(line, DiffLineType.CONTEXT))
            }
        }
        if (currentLines.isNotEmpty()) {
            hunks.add(DiffHunk(currentHeader, currentLines.toList()))
        }
        return hunks
    }

    fun isDiff(text: String): Boolean =
        text.startsWith("diff --git") || text.contains("\n--- ") || text.contains("\n@@ ")
}

@Composable
fun DiffContent(
    diffText: String,
    modifier: Modifier = Modifier,
) {
    val hunks = DiffParser.parse(diffText)
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
        ) {
            hunks.forEach { hunk ->
                if (hunk.header.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2D2D2D))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = hunk.header,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF569CD6),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                hunk.lines.forEach { line ->
                    val bgColor = when (line.type) {
                        DiffLineType.ADDED -> Color(0x2E6A8759)
                        DiffLineType.REMOVED -> Color(0x2ECC7832)
                        DiffLineType.CONTEXT -> Color.Transparent
                        DiffLineType.HEADER -> Color(0xFF2D2D2D)
                    }
                    val textColor = when (line.type) {
                        DiffLineType.ADDED -> Color(0xFF6A8759)
                        DiffLineType.REMOVED -> Color(0xFFCC7832)
                        DiffLineType.CONTEXT -> Color(0xFFA9B7C6)
                        DiffLineType.HEADER -> Color(0xFF569CD6)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        val prefix = when (line.type) {
                            DiffLineType.ADDED -> "+"
                            DiffLineType.REMOVED -> "-"
                            DiffLineType.CONTEXT -> " "
                            DiffLineType.HEADER -> ""
                        }
                        Text(
                            text = "$prefix${line.content.drop(1)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textColor,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}
