package com.rk.markdown

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.size.Size
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCheckBox
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * Default image loader for markdown images.
 *
 * This mirrors the library's `Coil2ImageTransformerImpl`, but uses an [ImageLoader] with an SVG decoder
 * so that `.svg` images referenced by markdown render as well. This matters here because the app does not
 * register an SVG decoder on the global Coil loader.
 *
 * Callers that need inline `data:` URIs - which Coil cannot fetch on its own - should pass a custom
 * [ImageTransformer] that delegates back to this one.
 */
object AppMarkdownImageTransformer : ImageTransformer {
    private var imageLoader: ImageLoader? = null

    private fun loader(context: Context): ImageLoader =
        imageLoader
            ?: ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build().also { imageLoader = it }

    @Composable
    override fun transform(link: String): ImageData? {
        val painter =
            rememberAsyncImagePainter(
                model = link,
                imageLoader = loader(LocalContext.current),
                contentScale = ContentScale.Fit,
            )
        return ImageData(painter, contentScale = ContentScale.Fit)
    }
}

/**
 * Heading scale (h1..h6, relative to the base text style) taken from sora-editor's
 * [io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer], so markdown rendered by this
 * Compose library matches the sizes used by hover/signature documentation.
 *
 * The library's own `markdownTypography()` defaults map h1/h2/h3 to the Material3 *display* styles
 * (57sp/45sp/36sp), which are far too large inside a chat bubble or a document body.
 */
private val HEADING_SCALE = floatArrayOf(1.6f, 1.4f, 1.25f, 1.1f, 1.05f, 1.0f)

/** GitHub uses a 1.5 line-height for body copy and 1.25 for headings. */
private const val BODY_LINE_HEIGHT = 1.5f
private const val HEADING_LINE_HEIGHT = 1.25f

/**
 * Builds Material3 markdown typography whose headings are scaled relative to [base] using
 * [HEADING_SCALE], instead of the library's oversized display-style defaults.
 *
 * Line breaking is set explicitly to match how a browser (and therefore GitHub) wraps prose:
 * [LineBreak.Paragraph] balances consecutive lines instead of greedily filling each one, which is
 * what removes the one-or-two-word last lines Compose produces by default. Headings use
 * [LineBreak.Heading] so they stay balanced, and code keeps [LineBreak.Simple] because pretty
 * wrapping is meaningless for monospaced blocks.
 *
 * Deliberately not `@Composable` so callers can hold the result in `remember` and avoid re-allocating
 * ~15 [TextStyle]s on every recomposition (which happens on every streamed token).
 */
private fun appMarkdownTypography(base: TextStyle): MarkdownTypography {
    fun scaledFont(multiplier: Float): TextUnit =
        if (base.fontSize.isSp || base.fontSize.isEm) base.fontSize * multiplier else base.fontSize

    fun scaledLineHeight(multiplier: Float): TextUnit =
        if (base.fontSize.isSp || base.fontSize.isEm) base.fontSize * multiplier else TextUnit.Unspecified

    val body =
        base.copy(
            lineHeight = scaledLineHeight(BODY_LINE_HEIGHT),
            lineBreak = LineBreak.Paragraph,
        )

    fun heading(scale: Float): TextStyle =
        base.copy(
            fontSize = scaledFont(scale),
            lineHeight = scaledLineHeight(scale * HEADING_LINE_HEIGHT),
            fontWeight = FontWeight.Bold,
            lineBreak = LineBreak.Heading,
        )

    val linkStyle = body.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)

    return DefaultMarkdownTypography(
        h1 = heading(HEADING_SCALE[0]),
        h2 = heading(HEADING_SCALE[1]),
        h3 = heading(HEADING_SCALE[2]),
        h4 = heading(HEADING_SCALE[3]),
        h5 = heading(HEADING_SCALE[4]),
        h6 = heading(HEADING_SCALE[5]),
        text = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        quote = body.copy(fontStyle = FontStyle.Italic),
        code = body.copy(fontFamily = FontFamily.Monospace, lineBreak = LineBreak.Simple),
        inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = TextUnit.Unspecified),
        textLink = TextLinkStyles(style = linkStyle.toSpanStyle()),
        table = body,
        alertTitle = body.copy(fontWeight = FontWeight.Bold),
    )
}

/**
 * The markdown component set used by every renderer in this file.
 *
 * Code fences and indented code blocks are syntax highlighted and rendered with the library's
 * `showHeader` top bar, which puts the language on the left and a copy-to-clipboard button on the
 * right - the same affordance the tool-output panels in the AI chat use.
 *
 * The `checkbox` entry is mandatory: passing a custom `components` value replaces the m3 `Markdown`
 * default, which is what normally installs the Material3 checkbox. Omitting it makes GFM task list
 * items fall back to the core renderer's literal `"[x] "` text.
 */
private fun appMarkdownComponents(): MarkdownComponents =
    markdownComponents(
        codeFence = { model ->
            MarkdownHighlightedCodeFence(
                content = model.content,
                node = model.node,
                style = model.typography.code,
                showHeader = true,
            )
        },
        codeBlock = { model ->
            MarkdownHighlightedCodeBlock(
                content = model.content,
                node = model.node,
                style = model.typography.code,
                showHeader = true,
            )
        },
        checkbox = { model ->
            val style = model.typography.text
            MarkdownCheckBox(
                content = model.content,
                node = model.node,
                style = style,
                checkedIndicator = { checked, indicatorModifier ->
                    MarkdownTaskCheckbox(
                        checked = checked,
                        fontSize = style.fontSize,
                        lineHeight = style.lineHeight,
                        modifier = indicatorModifier,
                    )
                },
            )
        },
    )

/**
 * A read-only task-list checkbox sized to the surrounding text.
 *
 * The library's Material3 variant uses a real `Checkbox`, which enforces a 48dp minimum touch
 * target: next to body copy the box ends up visibly larger than the line it belongs to. A GFM task
 * item is document content, not a form control, so the box is drawn at the text size instead and is
 * inset to sit centred on the first line.
 */
@Composable
private fun MarkdownTaskCheckbox(
    checked: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val em = if (fontSize.isSp) fontSize.value * density.fontScale else 0f
    val boxSize = if (em > 0f) (em * CHECKBOX_TEXT_RATIO).dp else DEFAULT_CHECKBOX_SIZE
    val line = if (lineHeight.isSp) lineHeight.value * density.fontScale else em * DEFAULT_LINE_HEIGHT
    val topInset = ((line - boxSize.value) / 2f).coerceAtLeast(0f).dp

    val color =
        if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }
    val shape = RoundedCornerShape(boxSize * CHECKBOX_CORNER_RATIO)

    Box(
        modifier =
            modifier
                .padding(top = topInset)
                .size(boxSize)
                .clip(shape)
                .background(if (checked) color else Color.Transparent)
                .border(BorderStroke(CHECKBOX_BORDER_WIDTH, color), shape)
                .semantics {
                    role = Role.Checkbox
                    stateDescription = if (checked) "Checked" else "Unchecked"
                },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxSize(CHECKBOX_TICK_RATIO),
            )
        }
    }
}

/** The box is a touch smaller than the text it marks. */
private const val CHECKBOX_TEXT_RATIO = 0.95f

private const val CHECKBOX_CORNER_RATIO = 0.25f

private const val CHECKBOX_TICK_RATIO = 0.72f

/** Fallbacks for a text style that does not carry a concrete size or line height. */
private val DEFAULT_CHECKBOX_SIZE = 14.dp

private const val DEFAULT_LINE_HEIGHT = 1.5f

private val CHECKBOX_BORDER_WIDTH = 1.5.dp

/**
 * Renders [content] as Markdown using the multiplatform-markdown-renderer library
 * (https://github.com/mikepenz/multiplatform-markdown-renderer).
 *
 * Although the library is a Kotlin Multiplatform project, it publishes Android AAR variants that
 * Gradle resolves automatically for this Android-only app, so it is consumed as a normal dependency
 * (see `gradle/libs.versions.toml`).
 *
 * Code fences and indented code blocks are syntax highlighted by the library's `-code` module.
 *
 * The renderer sizes itself to its content (it does not fill the available height), so it can be used
 * inside chat bubbles and lists. Pass [scrollable] to make it scroll on its own instead.
 *
 * [baseTextStyle] is the body text style; headings, quotes, lists and code are derived from it. Pass
 * e.g. `MaterialTheme.typography.bodyMedium` to match a compact surface such as an AI chat bubble.
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    baseTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    imageTransformer: ImageTransformer = AppMarkdownImageTransformer,
) {
    // Parse asynchronously and keep the previous content rendered while re-parsing.
    val markdownState = rememberMarkdownState(content = content, retainState = true)
    // Stable across recompositions: streaming updates the content on every token, and rebuilding
    // these on each pass would needlessly invalidate the library's CompositionLocals.
    val typography = remember(baseTextStyle) { appMarkdownTypography(baseTextStyle) }
    val components = remember { appMarkdownComponents() }

    val layoutModifier =
        if (scrollable) modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        else modifier.fillMaxWidth()

    Markdown(
        markdownState = markdownState,
        colors = markdownColor(),
        typography = typography,
        // The library default is 2.dp between blocks, which reads as cramped next to GitHub's 16px
        // paragraph margin. Blocks are also separated by EOL nodes that each add a spacer, so 8.dp
        // lands at roughly a GitHub-sized gap.
        padding = markdownPadding(block = 8.dp),
        imageTransformer = imageTransformer,
        components = components,
        modifier = layoutModifier,
        loading = { Box(it.fillMaxWidth()) },
        error = {
            Text(
                text = content,
                modifier = it.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}
