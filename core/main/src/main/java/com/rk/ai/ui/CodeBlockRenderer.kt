package com.rk.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.utils.ClipboardUtil
import kotlinx.coroutines.launch

private val codeBlockRegex = Regex("```(\\w*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)

data class MarkdownBlock(val type: MarkdownBlockType, val content: String, val language: String = "")

enum class MarkdownBlockType { TEXT, CODE, HEADING, LIST, QUOTE }

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var remaining = text

    while (remaining.isNotEmpty()) {
        val codeMatch = codeBlockRegex.find(remaining)
        if (codeMatch != null && codeMatch.range.first == 0) {
            val language = codeMatch.groupValues[1].trim()
            val code = codeMatch.groupValues[2].trimEnd()
            if (blocks.lastOrNull()?.type == MarkdownBlockType.TEXT) {
                val textContent = blocks.removeAt(blocks.lastIndex).content
                if (textContent.isNotBlank()) blocks.add(MarkdownBlock(MarkdownBlockType.TEXT, textContent))
            }
            blocks.add(MarkdownBlock(MarkdownBlockType.CODE, code, language))
            remaining = remaining.substring(codeMatch.range.last + 1)
            continue
        }

        val nextCode = codeBlockRegex.find(remaining)
        val textEnd = nextCode?.range?.first ?: remaining.length
        val textContent = remaining.substring(0, textEnd).trim()

        if (textContent.isNotBlank()) {
            textContent.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                when {
                    paragraph.startsWith("## ") -> blocks.add(MarkdownBlock(MarkdownBlockType.HEADING, paragraph.removePrefix("## ").trim()))
                    paragraph.startsWith("# ") -> blocks.add(MarkdownBlock(MarkdownBlockType.HEADING, paragraph.removePrefix("# ").trim()))
                    paragraph.startsWith("- ") || paragraph.startsWith("* ") -> {
                        paragraph.split("\n").filter { it.startsWith("- ") || it.startsWith("* ") }.forEach { line ->
                            blocks.add(MarkdownBlock(MarkdownBlockType.LIST, line.removePrefix("- ").removePrefix("* ").trim()))
                        }
                    }
                    else -> blocks.add(MarkdownBlock(MarkdownBlockType.TEXT, paragraph))
                }
            }
        }

        remaining = if (nextCode != null) remaining.substring(textEnd) else ""
    }

    return blocks
}

@Composable
fun AiMarkdownRenderer(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block.type) {
                MarkdownBlockType.CODE -> CodeBlock(block)
                MarkdownBlockType.HEADING -> HeadingBlock(block)
                MarkdownBlockType.LIST -> ListBlock(block)
                MarkdownBlockType.QUOTE -> QuoteBlock(block)
                MarkdownBlockType.TEXT -> TextBlock(block)
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock) {
    val scope = rememberCoroutineScope()
    val copied = remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    color = Color(0xFF9E9E9E),
                    style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        ClipboardUtil.copy(block.content)
                        copied.value = true
                        scope.launch {
                            kotlinx.coroutines.delay(2000)
                            copied.value = false
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (copied.value) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = block.content,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFFD4D4D4),
                        lineHeight = 20.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: MarkdownBlock) {
    Text(
        text = block.content,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ListBlock(block: MarkdownBlock) {
    Row(modifier = Modifier.padding(start = 8.dp)) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = block.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuoteBlock(block: MarkdownBlock) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(0.dp, 4.dp, 4.dp, 0.dp),
    ) {
        Text(
            text = block.content,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun TextBlock(block: MarkdownBlock) {
    SelectionContainer {
        val styled = remember(block.content) {
            block.content
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "\$1")
                .replace(Regex("`([^`]+)`"), "\$1")
        }
        Text(
            text = styled,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}
