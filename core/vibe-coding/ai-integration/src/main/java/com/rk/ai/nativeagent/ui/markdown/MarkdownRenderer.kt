package com.rk.ai.nativeagent.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
) {
    // Detect diff output and render with diff viewer
    if (DiffParser.isDiff(text.trimStart())) {
        DiffContent(diffText = text, modifier = modifier)
        return
    }

    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            MarkdownBlockRenderer(block = block)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MarkdownBlockRenderer(block: MarkdownBlock) {
    val colorScheme = MaterialTheme.colorScheme
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)

    when (block) {
        is MarkdownBlock.Paragraph -> {
            InlineStyleText(
                text = block.text,
                styles = block.inlineStyles,
                style = bodyStyle,
                color = colorScheme.onSurface,
            )
        }

        is MarkdownBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                4 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            }
            InlineStyleText(
                text = block.text,
                styles = block.inlineStyles,
                style = style,
                color = colorScheme.onSurface,
            )
        }

        is MarkdownBlock.CodeBlock -> {
            CodeBlockRenderer(
                code = block.code,
                language = block.language,
            )
        }

        is MarkdownBlock.InlineCode -> {
            InlineCodeText(block.code)
        }

        is MarkdownBlock.UnorderedList -> {
            Column {
                block.items.forEach { styles ->
                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                    ) {
                        Text("  •  ", style = bodyStyle, color = colorScheme.primary)
                        InlineStyleText(
                            text = styles.joinToString("") { it.text },
                            styles = styles,
                            style = bodyStyle,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        is MarkdownBlock.OrderedList -> {
            Column {
                block.items.forEachIndexed { idx, styles ->
                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                    ) {
                        Text(
                            "  ${block.start + idx}.  ",
                            style = bodyStyle,
                            color = colorScheme.primary,
                        )
                        InlineStyleText(
                            text = styles.joinToString("") { it.text },
                            styles = styles,
                            style = bodyStyle,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        is MarkdownBlock.BlockQuote -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                shape = RoundedCornerShape(4.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
                Row(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .background(colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    InlineStyleText(
                        text = block.text,
                        styles = block.inlineStyles,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp,
                            fontStyle = FontStyle.Italic,
                        ),
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is MarkdownBlock.Table -> {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    block.headers.forEachIndexed { idx, header ->
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                // Rows
                block.rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        block.headers.indices.forEach { idx ->
                            Text(
                                text = row.getOrElse(idx) { "" },
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        is MarkdownBlock.HorizontalRule -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
        }

        is MarkdownBlock.Image -> {
            Text(
                text = "[Image: ${block.alt}]",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = colorScheme.primary,
            )
        }

        is MarkdownBlock.Link -> {
            ClickableText(
                text = buildAnnotatedString {
                    append(block.text)
                    addStyle(SpanStyle(
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ), 0, block.text.length)
                },
                style = MaterialTheme.typography.bodyMedium,
                onClick = { /* open URL: block.url */ },
            )
        }
    }
}

@Composable
private fun CodeBlockRenderer(
    code: String,
    language: String?,
) {
    val colors = SyntaxHighlighter.rememberColors()
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }
    val highlighted = remember(code, language, colors) {
        SyntaxHighlighter.highlightWithColors(code, language, colors)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.background,
        tonalElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.headerBar)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = language ?: "code",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    ),
                    color = colors.headerText,
                )
                Surface(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        showCopied = true
                    },
                    shape = RoundedCornerShape(4.dp),
                    color = colors.copyButton,
                ) {
                    Text(
                        text = if (showCopied) "Copied!" else "Copy",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.copyText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (showCopied) {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2000)
                        showCopied = false
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun InlineCodeText(code: String) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = code,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        ),
        color = colorScheme.primary,
        modifier = Modifier
            .background(
                colorScheme.primaryContainer.copy(alpha = 0.3f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun InlineStyleText(
    text: String,
    styles: List<InlineStyle>,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
) {
    if (styles.isEmpty()) {
        Text(text = text, style = style, color = color)
        return
    }
    if (styles.all { !it.bold && !it.italic && !it.code && !it.strikethrough && it.link == null }) {
        Text(text = text, style = style, color = color)
        return
    }

    val annotated = buildAnnotatedString {
        val spans = mutableListOf<Triple<SpanStyle, Int, Int>>()
        var pos = 0
        styles.forEach { s ->
            val start = pos
            append(s.text)
            pos += s.text.length
            spans.add(Triple(SpanStyle(
                color = if (s.code) MaterialTheme.colorScheme.primary else color,
                fontWeight = when {
                    s.bold && s.italic -> FontWeight.Bold
                    s.bold -> FontWeight.Bold
                    else -> style.fontWeight ?: FontWeight.Normal
                },
                fontStyle = when {
                    s.italic -> FontStyle.Italic
                    else -> style.fontStyle ?: FontStyle.Normal
                },
                fontFamily = when {
                    s.code -> FontFamily.Monospace
                    else -> style.fontFamily
                },
                textDecoration = when {
                    s.strikethrough -> TextDecoration.LineThrough
                    s.link != null -> TextDecoration.Underline
                    else -> TextDecoration.None
                },
                fontSize = if (s.code) 13.sp else style.fontSize,
            ), start, pos))
        }
        spans.forEach { addStyle(it.first, it.second, it.third) }
    }

    if (styles.any { it.link != null }) {
        val linkMap = mutableMapOf<String, String>()
        styles.filter { it.link != null }.forEach { linkMap[it.text] = it.link!! }
        Text(text = annotated, style = style)
    } else {
        Text(text = annotated, style = style)
    }
}
