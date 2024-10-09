/*
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 */
package io.github.rosemoe.sora.widget;

import static io.github.rosemoe.sora.graphics.GraphicCharacter.couldBeEmojiPart;
import static io.github.rosemoe.sora.graphics.GraphicCharacter.isCombiningCharacter;
import static io.github.rosemoe.sora.util.Numbers.stringSize;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.MutableIntList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.rosemoe.sora.annotations.UnsupportedUserUsage;
import io.github.rosemoe.sora.graphics.BubbleHelper;
import io.github.rosemoe.sora.graphics.BufferedDrawPoints;
import io.github.rosemoe.sora.graphics.CharPosDesc;
import io.github.rosemoe.sora.graphics.GraphicTextRow;
import io.github.rosemoe.sora.graphics.Paint;
import io.github.rosemoe.sora.lang.completion.snippet.SnippetItem;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.styling.CodeBlock;
import io.github.rosemoe.sora.lang.styling.EmptyReader;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.SpanFactory;
import io.github.rosemoe.sora.lang.styling.Spans;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.styling.TextStyle;
import io.github.rosemoe.sora.lang.styling.color.ResolvableColor;
import io.github.rosemoe.sora.lang.styling.line.LineAnchorStyle;
import io.github.rosemoe.sora.lang.styling.line.LineBackground;
import io.github.rosemoe.sora.lang.styling.line.LineGutterBackground;
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon;
import io.github.rosemoe.sora.lang.styling.line.LineStyles;
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs;
import io.github.rosemoe.sora.lang.styling.span.SpanExternalRenderer;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.text.FunctionCharacters;
import io.github.rosemoe.sora.text.UnicodeIterator;
import io.github.rosemoe.sora.text.bidi.Directions;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.util.LongArrayList;
import io.github.rosemoe.sora.util.MutableInt;
import io.github.rosemoe.sora.util.Numbers;
import io.github.rosemoe.sora.util.RendererUtils;
import io.github.rosemoe.sora.util.TemporaryCharBuffer;
import io.github.rosemoe.sora.widget.layout.Row;
import io.github.rosemoe.sora.widget.layout.RowIterator;
import io.github.rosemoe.sora.widget.layout.WordwrapLayout;
import io.github.rosemoe.sora.widget.rendering.RenderingConstants;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle;
import io.github.rosemoe.sora.widget.style.LineInfoPanelPosition;
import io.github.rosemoe.sora.widget.style.LineInfoPanelPositionMode;
import io.github.rosemoe.sora.widget.style.SelectionHandleStyle;

public class EditorRenderer {

    /**
     * When measuring text in wordwrap mode, we must use the max possible width of the character sequence
     * so that no character will be invisible after its styles are applied on actual drawing.
     * It's different from the {@link CodeEditor#defaultSpans}
     */
    private final static List<Span> sSpansForWordwrap = new ArrayList<>();

    static {
        sSpansForWordwrap.add(SpanFactory.obtain(0, TextStyle.makeStyle(0, 0, true, true, false)));
    }

    private final static int[] PRESSED_DRAWABLE_STATE = new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled};
    private final static int[] DEFAULT_DRAWABLE_STATE = new int[]{android.R.attr.state_enabled};

    private static final String LOG_TAG = "EditorRenderer";
    private final static int[] sDiagnosticsColorMapping = {0, EditorColorScheme.PROBLEM_TYPO, EditorColorScheme.PROBLEM_WARNING, EditorColorScheme.PROBLEM_ERROR};
    protected final BufferedDrawPoints bufferedDrawPoints;
    protected final Paint paintGeneral;
    protected final Paint paintOther;
    protected final Rect viewRect;
    private final RectF tmpRect;
    private final Path tmpPath;
    protected final Paint paintGraph;
    private final RectF verticalScrollBarRect;
    private final RectF horizontalScrollBarRect;
    private final LongArrayList postDrawLineNumbers = new LongArrayList();
    private final MutableIntList postDrawCurrentLines = new MutableIntList();
    private final LongArrayList matchedPositions = new LongArrayList();
    private final SparseArray<ContentLine> preloadedLines = new SparseArray<>();
    private final SparseArray<Directions> preloadedDirections = new SparseArray<>();
    private final CodeEditor editor;
    private final List<DiagnosticRegion> collectedDiagnostics = new ArrayList<>();
    protected List<CodeBlock> lastStuckLines;
    Paint.FontMetricsInt metricsText;
    @Nullable
    private Drawable horizontalScrollbarThumbDrawable;
    @Nullable
    private Drawable horizontalScrollbarTrackDrawable;
    @Nullable
    private Drawable verticalScrollbarThumbDrawable;
    @Nullable
    private Drawable verticalScrollbarTrackDrawable;
    private volatile long displayTimestamp;
    private Paint.FontMetricsInt metricsLineNumber;
    private Paint.FontMetricsInt metricsGraph;
    private int cachedGutterWidth;
    private Cursor cursor;
    protected ContentLine lineBuf;
    protected Content content;
    private volatile boolean renderingFlag;
    protected boolean basicDisplayMode;
    protected boolean forcedRecreateLayout;

    public EditorRenderer(@NonNull CodeEditor editor) {
        this.editor = editor;
        verticalScrollBarRect = new RectF();
        horizontalScrollBarRect = new RectF();

        bufferedDrawPoints = new BufferedDrawPoints();

        paintGeneral = new Paint(editor.isRenderFunctionCharacters());
        paintGeneral.setAntiAlias(true);
        paintOther = new Paint(false);
        paintOther.setStrokeWidth(this.editor.getDpUnit() * 1.8f);
        paintOther.setStrokeCap(Paint.Cap.ROUND);
        paintOther.setTypeface(Typeface.MONOSPACE);
        paintOther.setAntiAlias(true);
        paintGraph = new Paint(false);
        paintGraph.setAntiAlias(true);

        metricsText = paintGeneral.getFontMetricsInt();
        metricsLineNumber = paintOther.getFontMetricsInt();

        viewRect = new Rect();
        tmpRect = new RectF();
        tmpPath = new Path();

        onEditorFullTextUpdate();
    }

    public boolean isBasicDisplayMode() {
        return basicDisplayMode;
    }

    /**
     * Called when the editor text is changed by {@link CodeEditor#setText}
     */
    public void onEditorFullTextUpdate() {
        cursor = editor.getCursor();
        content = editor.getText();
    }

    public void draw(@NonNull Canvas canvas) {
        int saveCount = canvas.save();
        canvas.translate(editor.getOffsetX(), editor.getOffsetY());
        renderingFlag = true;
        try {
            drawView(canvas);
        } finally {
            renderingFlag = false;
        }
        canvas.restoreToCount(saveCount);
    }

    public void onSizeChanged(int width, int height) {
        viewRect.right = width;
        viewRect.bottom = height;
    }

    Paint getPaint() {
        return paintGeneral;
    }

    Paint getPaintOther() {
        return paintOther;
    }

    Paint getPaintGraph() {
        return paintGraph;
    }

    void setCachedLineNumberWidth(int width) {
        cachedGutterWidth = width;
    }

    public RectF getVerticalScrollBarRect() {
        return verticalScrollBarRect;
    }

    public RectF getHorizontalScrollBarRect() {
        return horizontalScrollBarRect;
    }

    public void setHorizontalScrollbarThumbDrawable(@Nullable Drawable drawable) {
        horizontalScrollbarThumbDrawable = drawable;
    }

    @Nullable
    public Drawable getHorizontalScrollbarThumbDrawable() {
        return horizontalScrollbarThumbDrawable;
    }

    public void setHorizontalScrollbarTrackDrawable(@Nullable Drawable drawable) {
        horizontalScrollbarTrackDrawable = drawable;
    }

    @Nullable
    public Drawable getHorizontalScrollbarTrackDrawable() {
        return horizontalScrollbarTrackDrawable;
    }

    public void setVerticalScrollbarThumbDrawable(@Nullable Drawable drawable) {
        this.verticalScrollbarThumbDrawable = drawable;
    }

    @Nullable
    public Drawable getVerticalScrollbarThumbDrawable() {
        return verticalScrollbarThumbDrawable;
    }

    public void setVerticalScrollbarTrackDrawable(@Nullable Drawable drawable) {
        verticalScrollbarTrackDrawable = drawable;
    }

    @Nullable
    public Drawable getVerticalScrollbarTrackDrawable() {
        return verticalScrollbarTrackDrawable;
    }

    public void setTextSizePxDirect(float size) {
        paintGeneral.setTextSizeWrapped(size);
        paintOther.setTextSize(size);
        paintGraph.setTextSize(size * editor.getProps().miniMarkerSizeFactor);
        metricsText = paintGeneral.getFontMetricsInt();
        metricsLineNumber = paintOther.getFontMetricsInt();
        metricsGraph = paintGraph.getFontMetricsInt();
        editor.getRenderContext().invalidateRenderNodes();
        updateTimestamp();
    }

    /**
     * Set text's typeface
     *
     * @param typefaceText New typeface
     */
    public void setTypefaceText(Typeface typefaceText) {
        if (typefaceText == null) {
            typefaceText = Typeface.DEFAULT;
        }
        paintGeneral.setTypefaceWrapped(typefaceText);
        metricsText = paintGeneral.getFontMetricsInt();
        editor.getRenderContext().invalidateRenderNodes();
        updateTimestamp();
        editor.createLayout();
        editor.invalidate();
    }

    public void setTypefaceLineNumber(Typeface typefaceLineNumber) {
        if (typefaceLineNumber == null) {
            typefaceLineNumber = Typeface.MONOSPACE;
        }
        paintOther.setTypeface(typefaceLineNumber);
        metricsLineNumber = paintOther.getFontMetricsInt();
        editor.invalidate();
    }

    public void setTextScaleX(float textScaleX) {
        paintGeneral.setTextScaleX(textScaleX);
        paintOther.setTextScaleX(textScaleX);
        onTextStyleUpdate();
    }

    public void setLetterSpacing(float letterSpacing) {
        paintGeneral.setLetterSpacing(letterSpacing);
        paintOther.setLetterSpacing(letterSpacing);
        onTextStyleUpdate();
    }

    protected void onTextStyleUpdate() {
        paintGeneral.setRenderFunctionCharacters(editor.isRenderFunctionCharacters());
        metricsGraph = paintGraph.getFontMetricsInt();
        metricsLineNumber = paintOther.getFontMetricsInt();
        metricsText = paintGeneral.getFontMetricsInt();
        editor.getRenderContext().invalidateRenderNodes();
        updateTimestamp();
        editor.createLayout();
        editor.invalidate();
    }

    /**
     * Update timestamp required for measuring cache
     */
    protected void updateTimestamp() {
        displayTimestamp = System.nanoTime();
    }

    protected void prepareLine(int line) {
        lineBuf = getLine(line);
    }

    protected ContentLine getLine(int line) {
        if (!renderingFlag) {
            return getLineDirect(line);
        }
        var line2 = preloadedLines.get(line);
        if (line2 == null) {
            line2 = content.getLine(line);
            preloadedLines.put(line, line2);
        }
        return line2;
    }

    protected Directions getLineDirections(int line) {
        if (!renderingFlag) {
            return content.getLineDirections(line);
        }
        var line2 = preloadedDirections.get(line);
        if (line2 == null) {
            line2 = content.getLineDirections(line);
            preloadedDirections.put(line, line2);
        }
        return line2;
    }

    ContentLine getLineDirect(int line) {
        return content.getLine(line);
    }

    int getColumnCount(int line) {
        return getLine(line).length();
    }

    // draw methods

    @RequiresApi(29)
    public void updateLineDisplayList(RenderNode renderNode, int line, Spans.Reader spans) {
        int columnCount = getColumnCount(line);
        float widthLine = measureText(lineBuf, line, 0, columnCount) + editor.getDpUnit() * 20;
        renderNode.setPosition(0, 0, (int) (widthLine + paintGraph.measureText("↵") * 1.5f), editor.getRowHeight());
        var canvas = renderNode.beginRecording();
        try {
            drawSingleTextLine(canvas, line, 0f, 0f, spans, false);
        } finally {
            renderNode.endRecording();
        }
    }

    protected void drawSingleTextLine(Canvas canvas, int line, float offsetX, float offsetY, Spans.Reader spans, boolean visibleOnly) {
        prepareLine(line);
        canvas.save();
        canvas.translate(0, offsetY);
        int columnCount = getColumnCount(line);
        if (spans == null || spans.getSpanCount() <= 0) {
            spans = EmptyReader.getInstance();
        }
        int spanOffset = 0;
        int row = 0;
        Span span = spans.getSpanAt(spanOffset);
        // Draw by spans
        long lastStyle = 0;
        while (columnCount > span.getColumn()) {
            int spanEnd = spanOffset + 1 >= spans.getSpanCount() ? columnCount : spans.getSpanAt(spanOffset + 1).getColumn();
            spanEnd = Math.min(columnCount, spanEnd);
            int paintStart = span.getColumn();
            int paintEnd = Math.min(columnCount, spanEnd);
            float width = measureText(lineBuf, line, paintStart, paintEnd - paintStart);

            if (offsetX + width > 0 || !visibleOnly) {
                SpanExternalRenderer renderer = span.getSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER);

                // Invoke external renderer preDraw
                if (renderer != null && renderer.requirePreDraw()) {
                    int saveCount = canvas.save();
                    canvas.translate(offsetX, editor.getRowTop(row));
                    canvas.clipRect(0f, 0f, width, editor.getRowHeight());
                    try {
                        renderer.draw(canvas, paintGeneral, editor.getColorScheme(), true);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Error while invoking external renderer", e);
                    }
                    canvas.restoreToCount(saveCount);
                }

                // Apply font style
                long styleBits = span.getStyleBits();
                if (span.getStyleBits() != lastStyle) {
                    paintGeneral.setFakeBoldText(TextStyle.isBold(styleBits));
                    if (TextStyle.isItalics(styleBits)) {
                        paintGeneral.setTextSkewX(RenderingConstants.TEXT_SKEW_X);
                    } else {
                        paintGeneral.setTextSkewX(0);
                    }
                    lastStyle = styleBits;
                }

                int backgroundColor = RendererUtils.getBackgroundColor(span, editor.getColorScheme());
                if (backgroundColor != 0 && paintStart != paintEnd) {
                    tmpRect.set(offsetX, editor.getRowTop(row), offsetX + width, editor.getRowBottom(row));
                    paintGeneral.setColor(backgroundColor);
                    canvas.drawRoundRect(tmpRect, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, paintGeneral);
                }


                // Draw text
                int foregroundColor = RendererUtils.getForegroundColor(span, editor.getColorScheme());
                drawRegionTextDirectional(canvas, offsetX, editor.getRowBaseline(row), line, paintStart, paintEnd, span.getColumn(), spanEnd, columnCount, foregroundColor);

                // Draw strikethrough
                if (TextStyle.isStrikeThrough(span.getStyle())) {
                    var strikethroughColor = editor.getColorScheme().getColor(EditorColorScheme.STRIKETHROUGH);
                    paintOther.setColor(strikethroughColor == 0 ? paintGeneral.getColor() : strikethroughColor);
                    canvas.drawLine(offsetX, editor.getRowTop(row) + editor.getRowHeight() / 2f, offsetX + width, editor.getRowTop(row) + editor.getRowHeight() / 2f, paintOther);
                }

                // Draw underline
                var underlineColor = span.getUnderlineColor();
                int underlineColorInt;
                if (underlineColor != null && (underlineColorInt = underlineColor.resolve(editor.getColorScheme())) != 0) {
                    tmpRect.bottom = editor.getRowBottom(row) - editor.getDpUnit() * 1;
                    tmpRect.top = tmpRect.bottom - editor.getRowHeight() * RenderingConstants.TEXT_UNDERLINE_WIDTH_FACTOR;
                    tmpRect.left = offsetX;
                    tmpRect.right = offsetX + width;
                    drawColor(canvas, underlineColorInt, tmpRect);
                }

                // Invoke external renderer postDraw
                if (renderer != null && renderer.requirePostDraw()) {
                    int saveCount = canvas.save();
                    canvas.translate(offsetX, editor.getRowTop(row));
                    canvas.clipRect(0f, 0f, width, editor.getRowHeight());
                    try {
                        renderer.draw(canvas, paintGeneral, editor.getColorScheme(), false);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Error while invoking external renderer", e);
                    }
                    canvas.restoreToCount(saveCount);
                }
            }

            offsetX += width;

            if (visibleOnly && offsetX > editor.getWidth()) {
                break;
            }

            if (paintEnd == columnCount) {
                break;
            }
            spanOffset++;
            if (spanOffset < spans.getSpanCount()) {
                span = spans.getSpanAt(spanOffset);
            } else {
                spanOffset--;
            }
        }

        int nonPrintableFlags = editor.getNonPrintablePaintingFlags();
        // Draw hard wrap
        if ((nonPrintableFlags & CodeEditor.FLAG_DRAW_LINE_SEPARATOR) != 0) {
            drawMiniGraph(canvas, offsetX, -1, "↵");
        }
        paintGeneral.setTextSkewX(0);
        paintGeneral.setFakeBoldText(false);
        canvas.restore();
    }

    public boolean hasSideHintIcons() {
        Styles styles;
        if ((styles = editor.getStyles()) != null) {
            if (styles.styleTypeCount != null) {
                var count = styles.styleTypeCount.get(LineSideIcon.class);
                if (count == null) {
                    return false;
                }
                return count.value > 0;
            }
        }
        return false;
    }

    /**
     * Paint the view on given Canvas
     *
     * @param canvas Canvas you want to draw
     */
    public void drawView(Canvas canvas) {
        cursor.updateCache(editor.getFirstVisibleLine());

        EditorColorScheme color = editor.getColorScheme();
        drawColor(canvas, color.getColor(EditorColorScheme.WHOLE_BACKGROUND), viewRect);

        float lineNumberWidth = editor.measureLineNumber(); // include line number margin
        var sideIconWidth = hasSideHintIcons() ? editor.getRowHeight() : 0f;
        float offsetX = -editor.getOffsetX() + editor.measureTextRegionOffset();
        float textOffset = offsetX;

        var gutterWidth = (int) (lineNumberWidth + sideIconWidth + editor.getDividerWidth() + editor.getDividerMarginLeft() + editor.getDividerMarginRight());
        if (editor.isWordwrap()) {
            if (cachedGutterWidth == 0) {
                cachedGutterWidth = gutterWidth;
            } else if (cachedGutterWidth != gutterWidth && !editor.getEventHandler().isScaling) {
                cachedGutterWidth = gutterWidth;
                editor.postInLifecycle(editor::requestLayoutIfNeeded);
                editor.createLayout(false);
            } else if (forcedRecreateLayout) {
                editor.createLayout();
                editor.postInLifecycle(editor::requestLayoutIfNeeded);
            }
        } else {
            cachedGutterWidth = 0;
            if (forcedRecreateLayout) {
                editor.createLayout();
            }
        }
        forcedRecreateLayout = false;

        prepareLines(editor.getFirstVisibleLine(), editor.getLastVisibleLine());
        buildMeasureCacheForLines(editor.getFirstVisibleLine(), editor.getLastVisibleLine(), displayTimestamp, true);
        var stuckLines = getStuckCodeBlocks();
        int stuckLineCount = stuckLines == null ? 0 : stuckLines.size();

        if (cursor.isSelected()) {
            editor.getInsertHandleDescriptor().setEmpty();
        } else {
            editor.getLeftHandleDescriptor().setEmpty();
            editor.getRightHandleDescriptor().setEmpty();
        }

        boolean lineNumberNotPinned = editor.isLineNumberEnabled() && (editor.isWordwrap() || !editor.isLineNumberPinned());

        var postDrawLineNumbers = this.postDrawLineNumbers;
        postDrawLineNumbers.clear();
        var postDrawCurrentLines = this.postDrawCurrentLines;
        postDrawCurrentLines.clear();
        List<DrawCursorTask> postDrawCursor = new ArrayList<>(3);
        MutableInt firstLn = editor.isFirstLineNumberAlwaysVisible() && editor.isWordwrap() ? new MutableInt(-1) : null;

        canvas.save();
        canvas.clipRect(0, stuckLineCount * editor.getRowHeight(), editor.getWidth(), editor.getHeight());
        drawRows(canvas, textOffset, postDrawLineNumbers, postDrawCursor, postDrawCurrentLines, firstLn);
        patchHighlightedDelimiters(canvas, textOffset);
        drawDiagnosticIndicators(canvas, offsetX);
        canvas.restore();

        offsetX = -editor.getOffsetX();

        int currentLineNumber = cursor.isSelected() ? -1 : cursor.getLeftLine();

        if (lineNumberNotPinned) {
            drawLineNumberBackground(canvas, offsetX, lineNumberWidth + sideIconWidth + editor.getDividerMarginLeft(), color.getColor(EditorColorScheme.LINE_NUMBER_BACKGROUND));
            int lineNumberColor = editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER);
            int currentLineBgColor = editor.getColorScheme().getColor(EditorColorScheme.CURRENT_LINE);
            if (editor.getCursorAnimator().isRunning() && editor.isEditable()) {
                tmpRect.bottom = editor.getCursorAnimator().animatedLineBottom() - editor.getOffsetY();
                tmpRect.top = tmpRect.bottom - editor.getCursorAnimator().animatedLineHeight();
                tmpRect.left = 0;
                tmpRect.right = (int) (textOffset - editor.getDividerMarginRight());
                drawColor(canvas, currentLineBgColor, tmpRect);
            }

            canvas.save();
            canvas.clipRect(0, stuckLineCount * editor.getRowHeight(), editor.getWidth(), editor.getHeight());
            for (int i = 0; i < postDrawCurrentLines.count(); i++) {
                drawRowBackground(canvas, currentLineBgColor, postDrawCurrentLines.get(i), (int) (textOffset - editor.getDividerMarginRight()));
            }
            // User defined gutter background
            drawUserGutterBackground(canvas, (int) (textOffset - editor.getDividerMarginRight()));
            drawSideIcons(canvas, offsetX + lineNumberWidth);
            canvas.restore();

            drawDivider(canvas, offsetX + lineNumberWidth + sideIconWidth + editor.getDividerMarginLeft(), color.getColor(EditorColorScheme.LINE_DIVIDER));

            canvas.save();
            canvas.clipRect(0, stuckLineCount * editor.getRowHeight(), editor.getWidth(), editor.getHeight());
            if (firstLn != null && firstLn.value != -1) {
                int bottom = editor.getRowBottom(0);
                float y;
                if (postDrawLineNumbers.size() == 0 || editor.getRowTop(IntPair.getSecond(postDrawLineNumbers.get(0))) - editor.getOffsetY() > bottom) {
                    // Free to draw at first line
                    y = (editor.getRowBottom(0) + editor.getRowTop(0)) / 2f - (metricsLineNumber.descent - metricsLineNumber.ascent) / 2f - metricsLineNumber.ascent;
                } else {
                    int row = IntPair.getSecond(postDrawLineNumbers.get(0));
                    y = (editor.getRowBottom(row - 1) + editor.getRowTop(row - 1)) / 2f - (metricsLineNumber.descent - metricsLineNumber.ascent) / 2f - metricsLineNumber.ascent - editor.getOffsetY();
                }
                paintOther.setTextAlign(editor.getLineNumberAlign());
                paintOther.setColor(firstLn.value == currentLineNumber ? color.getColor(EditorColorScheme.LINE_NUMBER_CURRENT) : lineNumberColor);
                var text = Integer.toString(firstLn.value + 1);
                switch (editor.getLineNumberAlign()) {
                    case LEFT:
                        canvas.drawText(text, offsetX, y, paintOther);
                        break;
                    case RIGHT:
                        canvas.drawText(text, offsetX + lineNumberWidth, y, paintOther);
                        break;
                    case CENTER:
                        canvas.drawText(text, offsetX + (lineNumberWidth + editor.getDividerMarginLeft()) / 2f, y, paintOther);
                }
            }
            for (int i = 0; i < postDrawLineNumbers.size(); i++) {
                long packed = postDrawLineNumbers.get(i);
                drawLineNumber(canvas, IntPair.getFirst(packed), IntPair.getSecond(packed), offsetX, lineNumberWidth, IntPair.getFirst(packed) == currentLineNumber ? color.getColor(EditorColorScheme.LINE_NUMBER_CURRENT) : lineNumberColor);
            }
            canvas.restore();
        }

        if (editor.isBlockLineEnabled()) {
            canvas.save();
            canvas.clipRect(0, stuckLineCount * editor.getRowHeight(), editor.getWidth(), editor.getHeight());
            if (editor.isWordwrap()) {
                drawSideBlockLine(canvas);
            } else {
                drawBlockLines(canvas, textOffset);
            }
            canvas.restore();
        }

        if (!editor.getCursorAnimator().isRunning()) {
            for (var action : postDrawCursor) {
                action.execute(canvas);
            }
        } else {
            drawSelectionOnAnimation(canvas);
        }

        drawStuckLines(canvas, stuckLines, textOffset);

        if (editor.isLineNumberEnabled() && !lineNumberNotPinned) {
            drawLineNumberBackground(canvas, 0, lineNumberWidth + sideIconWidth + editor.getDividerMarginLeft(), color.getColor(EditorColorScheme.LINE_NUMBER_BACKGROUND));

            canvas.save();
            canvas.clipRect(0, stuckLineCount * editor.getRowHeight(), editor.getWidth(), editor.getHeight());
            int lineNumberColor = editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER);
            int currentLineBgColor = editor.getColorScheme().getColor(EditorColorScheme.CURRENT_LINE);
            if (editor.getCursorAnimator().isRunning() && editor.isEditable()) {
                tmpRect.bottom = editor.getCursorAnimator().animatedLineBottom() - editor.getOffsetY();
                tmpRect.top = tmpRect.bottom - editor.getCursorAnimator().animatedLineHeight();
                tmpRect.left = 0;
                tmpRect.right = (int) (textOffset - editor.getDividerMarginRight());
                drawColor(canvas, currentLineBgColor, tmpRect);
            }
            for (int i = 0; i < postDrawCurrentLines.count(); i++) {
                drawRowBackground(canvas, currentLineBgColor, postDrawCurrentLines.get(i), (int) (textOffset - editor.getDividerMarginRight() + editor.getOffsetX()));
            }
            drawUserGutterBackground(canvas, (int) (textOffset - editor.getDividerMarginRight() + editor.getOffsetX()));
            drawSideIcons(canvas, lineNumberWidth);
            canvas.restore();

            drawDivider(canvas, lineNumberWidth + sideIconWidth + editor.getDividerMarginLeft(), color.getColor(EditorColorScheme.LINE_DIVIDER));

            canvas.save();
            canvas.clipRect(0, stuckLineCount * editor.getRowHeight(), editor.getWidth(), editor.getHeight());
            for (int i = 0; i < postDrawLineNumbers.size(); i++) {
                long packed = postDrawLineNumbers.get(i);
                drawLineNumber(canvas, IntPair.getFirst(packed), IntPair.getSecond(packed), 0, lineNumberWidth, IntPair.getFirst(packed) == currentLineNumber ? color.getColor(EditorColorScheme.LINE_NUMBER_CURRENT) : lineNumberColor);
            }
            canvas.restore();
        }

        drawStuckLineNumbers(canvas, stuckLines, offsetX, lineNumberWidth, editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER));
        drawScrollBars(canvas);
        drawEdgeEffect(canvas);

        releasePreloadedData();
        lastStuckLines = stuckLines;
        drawFormatTip(canvas);
    }

    protected void drawUserGutterBackground(Canvas canvas, int right) {
        int firstVis = editor.getFirstVisibleLine(), lastVis = editor.getLastVisibleLine();
        for (int line = firstVis; line <= lastVis; line++) {
            var bg = getUserGutterBackgroundForLine(line);
            if (bg != null) {
                var bgColor = bg.resolve(editor.getColorScheme());
                var top = (int) (editor.getLayout().getCharLayoutOffset(line, 0)[0] / editor.getRowHeight()) - 1;
                var count = editor.getLayout().getRowCountForLine(line);
                for (int i = 0; i < count; i++) {
                    drawRowBackground(canvas, bgColor, top + i, right);
                }
            }
        }
    }

    protected void drawStuckLineNumbers(Canvas canvas, List<CodeBlock> candidates, float offset, float lineNumberWidth, int lineNumberColor) {
        if (candidates == null || candidates.isEmpty() || !editor.isLineNumberEnabled()) {
            return;
        }
        var cursor = editor.getCursor();
        var currentLine = cursor.isSelected() ? -1 : cursor.getLeftLine();
        canvas.save();
        var offsetY = editor.getOffsetY();
        canvas.translate(0, offsetY);
        for (int i = 0; i < candidates.size(); i++) {
            var line = candidates.get(i).startLine;
            var bg = getUserGutterBackgroundForLine(line);
            var color = bg != null ? bg.resolve(editor.getColorScheme()) : 0;
            if (currentLine == line || color != 0) {
                tmpRect.top = editor.getRowTop(i) - offsetY;
                tmpRect.bottom = editor.getRowBottom(i) - offsetY - editor.getDpUnit();
                tmpRect.left = editor.isLineNumberPinned() ? 0 : offset;
                tmpRect.right = tmpRect.left + editor.measureTextRegionOffset();
                if (currentLine == line)
                    drawColor(canvas, editor.getColorScheme().getColor(EditorColorScheme.CURRENT_LINE), tmpRect);
                if (color != 0)
                    drawColor(canvas, color, tmpRect);
            }
            drawLineNumber(canvas, line, i,
                    editor.isLineNumberPinned() ? 0 : offset, lineNumberWidth,
                    currentLine == line ? editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER_CURRENT) : lineNumberColor);
        }
        canvas.restore();
    }

    protected void drawStuckLines(Canvas canvas, List<CodeBlock> candidates, float offset) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        var styles = editor.getStyles();
        var spanMap = styles != null ? styles.spans : null;
        var spanReader = spanMap != null ? spanMap.read() : null;
        var previousLine = -1;
        var offsetLine = 0;
        var cursor = editor.getCursor();
        var currentLine = cursor.isSelected() ? -1 : cursor.getLeftLine();
        for (int i = 0; i < candidates.size(); i++) {
            var block = candidates.get(i);
            if (block.startLine > previousLine) {
                tmpRect.top = editor.getRowTop(offsetLine);
                tmpRect.bottom = editor.getRowBottom(offsetLine);
                tmpRect.left = offset;
                tmpRect.right = editor.getWidth();
                var colorId = EditorColorScheme.WHOLE_BACKGROUND;
                if (block.startLine == currentLine) {
                    colorId = EditorColorScheme.CURRENT_LINE;
                }
                drawColor(canvas, editor.getColorScheme().getColor(colorId), tmpRect);
                try {
                    if (spanReader != null) {
                        spanReader.moveToLine(block.startLine);
                    }
                    drawSingleTextLine(canvas, block.startLine, offset, offsetLine * editor.getRowHeight(), spanReader, true);
                } finally {
                    if (spanReader != null) {
                        spanReader.moveToLine(-1);
                    }
                }
                previousLine = block.startLine;
                offsetLine++;
            }
        }
        if (offsetLine > 0) {
            tmpRect.top = editor.getRowTop(offsetLine) - editor.getDpUnit();
            tmpRect.bottom = editor.getRowTop(offsetLine);
            tmpRect.left = 0;
            tmpRect.right = editor.getWidth();
            drawColor(canvas, editor.getColorScheme().getColor(EditorColorScheme.STICKY_SCROLL_DIVIDER), tmpRect);
        }
    }

    protected void drawHardwrapMarker(Canvas canvas, float offset) {
        int column = editor.getProps().hardwrapColumn;
        if (!editor.isWordwrap() && column > 0) {
            tmpRect.left = offset + paintGeneral.measureText("a") * column;
            tmpRect.right = tmpRect.left + editor.getDpUnit() * 2f;
            tmpRect.top = 0f;
            tmpRect.bottom = viewRect.bottom;
            drawColor(canvas, editor.getColorScheme().getColor(EditorColorScheme.HARD_WRAP_MARKER), tmpRect);
        }
    }

    protected void drawSideIcons(Canvas canvas, float offset) {
        if (!hasSideHintIcons()) {
            return;
        }
        var row = editor.getFirstVisibleRow();
        var itr = editor.getLayout().obtainRowIterator(row);
        final var iconSizeFactor = editor.getProps().sideIconSizeFactor;
        var size = (int) (editor.getRowHeight() * iconSizeFactor);
        var offsetToLeftTop = (int) (editor.getRowHeight() * (1 - iconSizeFactor) / 2f);
        while (row <= editor.getLastVisibleRow() && itr.hasNext()) {
            var rowInf = itr.next();
            if (rowInf.isLeadingRow) {
                var hint = getLineStyle(rowInf.lineIndex, LineSideIcon.class);
                if (hint != null) {
                    var drawable = hint.getDrawable();
                    var rect = new Rect(0, 0, size, size);
                    rect.offsetTo((int) offset + offsetToLeftTop, editor.getRowTop(row) - editor.getOffsetY() + offsetToLeftTop);
                    drawable.setBounds(rect);
                    drawable.draw(canvas);
                }
            }
            row++;
        }
    }

    protected void drawFormatTip(Canvas canvas) {
        if (editor.isFormatting()) {
            String text = editor.getFormatTip();
            float baseline = editor.getRowBaseline(0);
            float rightX = editor.getWidth();
            paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.TEXT_NORMAL));
            paintGeneral.setFakeBoldText(true);
            paintGeneral.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(text, rightX, baseline, paintGeneral);
            paintGeneral.setTextAlign(Paint.Align.LEFT);
            paintGeneral.setFakeBoldText(false);
        }
    }

    /**
     * Draw rect on screen
     * Will not do anything if color is zero
     *
     * @param canvas Canvas to draw
     * @param color  Color of rect
     * @param rect   Rect to draw
     */
    protected void drawColor(Canvas canvas, int color, RectF rect) {
        if (color != 0) {
            paintGeneral.setColor(color);
            canvas.drawRect(rect, paintGeneral);
        }
    }

    /**
     * Draw rect on screen in a round rectangle
     * Will not do anything if color is zero
     *
     * @param canvas Canvas to draw
     * @param color  Color of rect
     * @param rect   Rect to draw
     */
    protected void drawColorRound(Canvas canvas, int color, RectF rect) {
        if (color != 0) {
            paintGeneral.setColor(color);
            canvas.drawRoundRect(rect, rect.height() * RenderingConstants.ROUND_RECT_FACTOR, rect.height() * RenderingConstants.ROUND_RECT_FACTOR, paintGeneral);
        }
    }

    /**
     * Draw rect on screen
     * Will not do anything if color is zero
     *
     * @param canvas Canvas to draw
     * @param color  Color of rect
     * @param rect   Rect to draw
     */
    protected void drawColor(Canvas canvas, int color, Rect rect) {
        if (color != 0) {
            paintGeneral.setColor(color);
            canvas.drawRect(rect, paintGeneral);
        }
    }

    /**
     * Draw background for whole row
     */
    protected void drawRowBackground(Canvas canvas, int color, int row) {
        drawRowBackground(canvas, color, row, viewRect.right);
    }

    protected void drawRowBackground(Canvas canvas, int color, int row, int right) {
        tmpRect.top = editor.getRowTop(row) - editor.getOffsetY();
        tmpRect.bottom = editor.getRowBottom(row) - editor.getOffsetY();
        tmpRect.left = 0;
        tmpRect.right = right;
        drawColor(canvas, color, tmpRect);
    }

    /**
     * Draw single line number
     */
    protected void drawLineNumber(Canvas canvas, int line, int row, float offsetX, float width, int color) {
        if (width + offsetX <= 0) {
            return;
        }
        if (paintOther.getTextAlign() != editor.getLineNumberAlign()) {
            paintOther.setTextAlign(editor.getLineNumberAlign());
        }
        paintOther.setColor(color);
        // Line number center align to text center
        float y = (editor.getRowBottom(row) + editor.getRowTop(row)) / 2f - (metricsLineNumber.descent - metricsLineNumber.ascent) / 2f - metricsLineNumber.ascent - editor.getOffsetY();

        var buffer = TemporaryCharBuffer.obtain(20);
        line++;
        int i = stringSize(line);
        Numbers.getChars(line, i, buffer);

        switch (editor.getLineNumberAlign()) {
            case LEFT:
                canvas.drawText(buffer, 0, i, offsetX, y, paintOther);
                break;
            case RIGHT:
                canvas.drawText(buffer, 0, i, offsetX + width, y, paintOther);
                break;
            case CENTER:
                canvas.drawText(buffer, 0, i, offsetX + (width + editor.getDividerMarginLeft()) / 2f, y, paintOther);
        }
        TemporaryCharBuffer.recycle(buffer);
    }

    /**
     * Draw line number background
     *
     * @param canvas  Canvas to draw
     * @param offsetX Start x of line number region
     * @param width   Width of line number region
     * @param color   Color of line number background
     */
    protected void drawLineNumberBackground(Canvas canvas, float offsetX, float width, int color) {
        float right = offsetX + width;
        if (right < 0) {
            return;
        }
        float left = Math.max(0f, offsetX);
        tmpRect.bottom = editor.getHeight();
        tmpRect.top = 0;
        int offY = editor.getOffsetY();
        if (offY < 0) {
            tmpRect.bottom = tmpRect.bottom - offY;
            tmpRect.top = tmpRect.top - offY;
        }
        tmpRect.left = left;
        tmpRect.right = right;
        drawColor(canvas, color, tmpRect);
    }

    /**
     * Draw divider line
     *
     * @param canvas  Canvas to draw
     * @param offsetX End x of line number region
     * @param color   Color to draw divider
     */
    protected void drawDivider(Canvas canvas, float offsetX, int color) {
        boolean shadow = editor.isLineNumberPinned() && !editor.isWordwrap() && editor.getOffsetX() > 0;
        float right = offsetX + editor.getDividerWidth();
        if (right < 0) {
            return;
        }
        float left = Math.max(0f, offsetX);
        tmpRect.bottom = editor.getHeight();
        tmpRect.top = 0;
        int offY = editor.getOffsetY();
        if (offY < 0) {
            tmpRect.bottom = tmpRect.bottom - offY;
            tmpRect.top = tmpRect.top - offY;
        }
        tmpRect.left = left;
        tmpRect.right = right;
        if (shadow) {
            canvas.save();
            canvas.clipRect(tmpRect.left, tmpRect.top, editor.getWidth(), tmpRect.bottom);
            paintGeneral.setShadowLayer(Math.min(editor.getDpUnit() * RenderingConstants.DIVIDER_SHADOW_MAX_RADIUS_DIP, editor.getOffsetX()), 0, 0, Color.BLACK);
        }
        drawColor(canvas, color, tmpRect);
        if (shadow) {
            canvas.restore();
            paintGeneral.setShadowLayer(0, 0, 0, 0);
        }
    }

    private void prepareLines(int start, int end) {
        releasePreloadedData();
        content.runReadActionsOnLines(Math.max(0, start - 5), Math.min(content.getLineCount() - 1, end + 5), (int i, ContentLine line, Directions dirs) -> {
            preloadedLines.put(i, line);
            preloadedDirections.put(i, dirs);
        });
    }

    private void releasePreloadedData() {
        preloadedLines.clear();
        preloadedDirections.clear();
    }

    protected List<CodeBlock> getStuckCodeBlocks() {
        if (editor.isWordwrap() || !editor.getProps().stickyScroll) {
            return null;
        }
        Styles styles;
        int startLine = editor.getFirstVisibleLine();
        int offsetY = editor.getOffsetY(), rowHeight = editor.getRowHeight();
        List<CodeBlock> codeBlocks;
        if ((styles = editor.getStyles()) == null || (codeBlocks = styles.blocksByStart) == null) {
            return null;
        }
        int size = codeBlocks.size();
        List<CodeBlock> candidates = new ArrayList<>();
        var limit = editor.getProps().stickyScrollIterationLimit;
        for (int i = 0; i < size && i < limit; i++) {
            var block = codeBlocks.get(i);
            if (block == null) {
                continue;
            }
            if (block.startLine > startLine) {
                break;
            }
            if (block.endLine > startLine && editor.getRowTop(block.startLine) - offsetY < 0) {
                candidates.add(block);
                startLine++;
                offsetY += rowHeight;
            }
        }
        var maxLines = editor.getProps().stickyScrollMaxLines;
        if (candidates.size() > maxLines) {
            if (maxLines <= 0) {
                return null;
            }
            if (editor.getProps().stickyScrollPreferInnerScope) {
                candidates = candidates.subList(candidates.size() - maxLines, candidates.size());
            } else {
                candidates = candidates.subList(0, maxLines);
            }
        }
        if (editor.getCursor().isSelected() && editor.getProps().stickyScrollAutoCollapse) {
            var limitLine = editor.getCursor().getLeftLine();
            var firstVis = editor.getFirstVisibleLine();
            int lastSelectionLine = editor.getCursor().getRightLine();
            if (lastSelectionLine >= firstVis) {
                while (!candidates.isEmpty() && firstVis + candidates.size() >= limitLine) {
                    candidates.remove(candidates.size() - 1);
                }
            }
        }
        return candidates;
    }

    private final LineStyles coordinateLine = new LineStyles(0);

    @Nullable
    protected LineStyles getLineStyles(int line) {
        Styles styles;
        List<LineStyles> lineStylesList;
        if ((styles = editor.getStyles()) == null || (lineStylesList = styles.lineStyles) == null) {
            return null;
        }
        coordinateLine.setLine(line);
        var index = Collections.binarySearch(lineStylesList, coordinateLine);
        if (index >= 0 && index < lineStylesList.size()) {
            return lineStylesList.get(index);
        }
        return null;
    }

    @Nullable
    protected <T extends LineAnchorStyle> T getLineStyle(int line, Class<T> type) {
        var lineStyles = getLineStyles(line);
        if (lineStyles != null) {
            return lineStyles.findOne(type);
        }
        return null;
    }

    @Nullable
    protected ResolvableColor getUserBackgroundForLine(int line) {
        var bg = getLineStyle(line, LineBackground.class);
        if (bg != null) {
            return bg.getColor();
        }
        return null;
    }

    @Nullable
    protected ResolvableColor getUserGutterBackgroundForLine(int line) {
        var bg = getLineStyle(line, LineGutterBackground.class);
        if (bg != null) {
            return bg.getColor();
        }
        return null;
    }

    /**
     * Draw rows with a {@link RowIterator}
     *
     * @param canvas              Canvas to draw
     * @param offset              Offset of text region start
     * @param postDrawLineNumbers Line numbers to be drawn later
     * @param postDrawCursor      Cursors to be drawn later
     */
    protected void drawRows(Canvas canvas, float offset, LongArrayList postDrawLineNumbers, List<DrawCursorTask> postDrawCursor, MutableIntList postDrawCurrentLines, MutableInt requiredFirstLn) {
        int firstVis = editor.getFirstVisibleRow();
        RowIterator rowIterator = editor.getLayout().obtainRowIterator(firstVis, preloadedLines);
        Spans spans = editor.getStyles() == null ? null : editor.getStyles().spans;
        var matchedPositions = this.matchedPositions;
        matchedPositions.clear();
        int currentLine = cursor.isSelected() ? -1 : cursor.getLeftLine();
        int currentLineBgColor = editor.getColorScheme().getColor(EditorColorScheme.CURRENT_LINE);
        int lastPreparedLine = -1;
        int spanOffset = 0;
        int leadingWhitespaceEnd = 0;
        int trailingWhitespaceStart = 0;
        float circleRadius = 0f;
        var composingPosition = editor.inputConnection.composingText.isComposing() && editor.inputConnection.composingText.startIndex >= 0 && editor.inputConnection.composingText.startIndex < content.length() ? content.getIndexer().getCharPosition(editor.inputConnection.composingText.startIndex) : null;
        var composingLength = editor.inputConnection.composingText.endIndex - editor.inputConnection.composingText.startIndex;
        var draggingSelection = editor.getEventHandler().draggingSelection;
        if (editor.shouldInitializeNonPrintable()) {
            float spaceWidth = paintGeneral.getSpaceWidth();
            circleRadius = Math.min(editor.getRowHeight(), spaceWidth) * RenderingConstants.NON_PRINTABLE_CIRCLE_RADIUS_FACTOR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !editor.isWordwrap() && canvas.isHardwareAccelerated() && editor.isHardwareAcceleratedDrawAllowed()) {
            editor.getRenderContext().getRenderNodeHolder().keepCurrentInDisplay(firstVis, editor.getLastVisibleRow());
        }
        float offset2 = editor.getOffsetX() - editor.measureTextRegionOffset();
        float offset3 = offset2 - editor.getDpUnit() * 15;

        // Step 1 - Draw background of rows

        // Draw current line background on animation
        if (editor.getCursorAnimator().isRunning()) {
            tmpRect.bottom = editor.getCursorAnimator().animatedLineBottom() - editor.getOffsetY();
            tmpRect.top = tmpRect.bottom - editor.getCursorAnimator().animatedLineHeight();
            tmpRect.left = 0;
            tmpRect.right = viewRect.right;
            drawColor(canvas, currentLineBgColor, tmpRect);
        }
        // Other backgrounds
        for (int row = firstVis; row <= editor.getLastVisibleRow() && rowIterator.hasNext(); row++) {
            Row rowInf = rowIterator.next();
            int line = rowInf.lineIndex;
            int columnCount = getColumnCount(line);
            if (lastPreparedLine != line) {
                editor.computeMatchedPositions(line, matchedPositions);
                prepareLine(line);
                lastPreparedLine = line;
            }
            // Get visible region on the line
            long charPos = findDesiredVisibleChar(offset3, line, rowInf.startColumn, rowInf.endColumn);
            float paintingOffset = CharPosDesc.getPixelWidthOrOffset(charPos) - offset2;

            var drawCurrentLineBg = line == currentLine && !editor.getCursorAnimator().isRunning() && editor.isEditable();
            if (!drawCurrentLineBg || editor.getProps().drawCustomLineBgOnCurrentLine) {
                // Draw custom background
                var customBackground = getUserBackgroundForLine(line);
                if (customBackground != null) {
                    var color = customBackground.resolve(editor.getColorScheme());
                    drawRowBackground(canvas, color, row);
                }
            }
            if (drawCurrentLineBg) {
                // Draw current line background
                drawRowBackground(canvas, currentLineBgColor, row);
                postDrawCurrentLines.add(row);
            }

            // Draw matched text background
            if (matchedPositions.size() > 0) {
                for (int i = 0; i < matchedPositions.size(); i++) {
                    var position = matchedPositions.get(i);
                    var start = IntPair.getFirst(position);
                    var end = IntPair.getSecond(position);
                    drawRowRegionBackground(canvas, row, line, start, end, rowInf.startColumn, rowInf.endColumn, editor.getColorScheme().getColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND));
                }
            }

            // Draw selected text background
            if (cursor.isSelected() && line >= cursor.getLeftLine() && line <= cursor.getRightLine()) {
                int selectionStart = 0;
                int selectionEnd = columnCount;
                if (line == cursor.getLeftLine()) {
                    selectionStart = cursor.getLeftColumn();
                }
                if (line == cursor.getRightLine()) {
                    selectionEnd = cursor.getRightColumn();
                }
                if (getColumnCount(line) == 0 && line != cursor.getRightLine()) {
                    tmpRect.top = editor.getRowTop(row) - editor.getOffsetY();
                    tmpRect.bottom = editor.getRowBottom(row) - editor.getOffsetY();
                    tmpRect.left = paintingOffset;
                    tmpRect.right = tmpRect.left + paintGeneral.getSpaceWidth() * 2;
                    paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND));
                    canvas.drawRoundRect(tmpRect, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, paintGeneral);
                } else if (selectionStart < selectionEnd) {
                    drawRowRegionBackground(canvas, row, line, selectionStart, selectionEnd, rowInf.startColumn, rowInf.endColumn, editor.getColorScheme().getColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND));
                }
            }
        }
        rowIterator.reset();

        // Background of snippets
        patchSnippetRegions(canvas, offset);

        // Hard wrap marker
        drawHardwrapMarker(canvas, offset);

        // Step 2 - Draw text and text decorations
        long lastStyle = 0;
        Spans.Reader reader = null;
        lastPreparedLine = -1;
        for (int row = firstVis; row <= editor.getLastVisibleRow() && rowIterator.hasNext(); row++) {
            Row rowInf = rowIterator.next();
            int line = rowInf.lineIndex;
            ContentLine contentLine = getLine(line);
            int columnCount = contentLine.length();
            if (row == firstVis && requiredFirstLn != null) {
                requiredFirstLn.value = line;
            } else if (rowInf.isLeadingRow) {
                postDrawLineNumbers.add(IntPair.pack(line, row));
            }

            // Prepare data
            if (lastPreparedLine != line) {
                lastPreparedLine = line;
                prepareLine(line);
                spanOffset = 0;
                // Release old reader
                if (reader != null) {
                    try {
                        reader.moveToLine(-1);
                    } catch (Exception e) {
                        Log.w(LOG_TAG, "Failed to release SpanReader", e);
                    }
                }
                // Get new reader and lock
                // Note that we should hold the reader during the **text line** rendering process
                // Otherwise, the spans of that line can be changed during the inter rendering time
                // between two **rows** because the spans could have been changed concurrently
                // See #290
                reader = spans == null ? EmptyReader.getInstance() : spans.read();
                try {
                    reader.moveToLine(line);
                } catch (Exception e) {
                    Log.w(LOG_TAG, "Failed to read span", e);
                    reader = EmptyReader.getInstance();
                }
                if (reader.getSpanCount() == 0) {
                    // Unacceptable span count, use fallback reader
                    reader = EmptyReader.getInstance();
                }
                if (editor.shouldInitializeNonPrintable()) {
                    long positions = editor.findLeadingAndTrailingWhitespacePos(lineBuf);
                    leadingWhitespaceEnd = IntPair.getFirst(positions);
                    trailingWhitespaceStart = IntPair.getSecond(positions);
                }
            }

            // Get visible region on the line
            long charPos = findDesiredVisibleChar(offset3, line, rowInf.startColumn, rowInf.endColumn);
            int firstVisibleChar = CharPosDesc.getTextOffset(charPos);
            float paintingOffset = CharPosDesc.getPixelWidthOrOffset(charPos) - offset2;
            charPos = findDesiredVisibleChar(editor.getWidth() - paintingOffset, line, firstVisibleChar, rowInf.endColumn, rowInf.startColumn, true);
            int lastVisibleChar = CharPosDesc.getTextOffset(charPos);

            float backupOffset = paintingOffset;
            int nonPrintableFlags = editor.getNonPrintablePaintingFlags();

            // Draw text here
            if (!editor.isHardwareAcceleratedDrawAllowed()
                    || editor.getEventHandler().isScaling ||
                    !canvas.isHardwareAccelerated() || editor.isWordwrap() ||
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    || (rowInf.endColumn - rowInf.startColumn > 128 && !editor.getProps().cacheRenderNodeForLongLines) /* Save memory */) {
                // Draw without hardware acceleration
                // Seek for first span
                while (spanOffset + 1 < reader.getSpanCount()) {
                    if (reader.getSpanAt(spanOffset + 1).getColumn() <= firstVisibleChar) {
                        spanOffset++;
                    } else {
                        break;
                    }
                }
                Span span = reader.getSpanAt(spanOffset);
                // Draw by spans
                while (lastVisibleChar > span.getColumn()) {
                    int spanEnd = spanOffset + 1 >= reader.getSpanCount()
                            ? columnCount : reader.getSpanAt(spanOffset + 1).getColumn();
                    spanEnd = Math.min(columnCount, spanEnd);
                    int paintStart = Math.max(firstVisibleChar, span.getColumn());
                    paintStart = Math.max(0, paintStart);
                    if (paintStart >= columnCount) {
                        break;
                    }
                    int paintEnd = Math.min(lastVisibleChar, spanEnd);
                    paintEnd = Math.min(columnCount, paintEnd);
                    if (paintStart > paintEnd) {
                        break;
                    }
                    float width = measureText(lineBuf, line, paintStart, paintEnd - paintStart);
                    SpanExternalRenderer renderer = span.getSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER);

                    // Invoke external renderer preDraw
                    if (renderer != null && renderer.requirePreDraw()) {
                        int saveCount = canvas.save();
                        canvas.translate(paintingOffset, editor.getRowTop(row) - editor.getOffsetY());
                        canvas.clipRect(0f, 0f, width, editor.getRowHeight());
                        try {
                            renderer.draw(canvas, paintGeneral, editor.getColorScheme(), true);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Error while invoking external renderer", e);
                        }
                        canvas.restoreToCount(saveCount);
                    }

                    // Apply font style
                    long styleBits = span.getStyleBits();
                    if (span.getStyleBits() != lastStyle) {
                        paintGeneral.setFakeBoldText(TextStyle.isBold(styleBits));
                        if (TextStyle.isItalics(styleBits)) {
                            paintGeneral.setTextSkewX(RenderingConstants.TEXT_SKEW_X);
                        } else {
                            paintGeneral.setTextSkewX(0);
                        }
                        lastStyle = styleBits;
                    }

                    int backgroundColor = RendererUtils.getBackgroundColor(span, editor.getColorScheme());
                    if (backgroundColor != 0 && paintStart != paintEnd) {
                        tmpRect.top = editor.getRowTop(row) - editor.getOffsetY();
                        tmpRect.bottom = editor.getRowBottom(row) - editor.getOffsetY();
                        tmpRect.left = paintingOffset;
                        tmpRect.right = tmpRect.left + width;
                        paintGeneral.setColor(backgroundColor);
                        canvas.drawRoundRect(tmpRect, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, paintGeneral);
                    }

                    // Draw text
                    int foregroundColor = RendererUtils.getForegroundColor(span, editor.getColorScheme());
                    drawRegionTextDirectional(canvas, paintingOffset, editor.getRowBaseline(row) - editor.getOffsetY(), line, paintStart, paintEnd, span.getColumn(), spanEnd, columnCount, foregroundColor);

                    // Draw strikethrough
                    if (TextStyle.isStrikeThrough(styleBits)) {
                        var strikethroughColor = editor.getColorScheme().getColor(EditorColorScheme.STRIKETHROUGH);
                        paintOther.setColor(strikethroughColor == 0 ? paintGeneral.getColor() : strikethroughColor);
                        canvas.drawLine(paintingOffset, editor.getRowTop(row) + editor.getRowHeight() / 2f - editor.getOffsetY(), paintingOffset + width, editor.getRowTop(row) + editor.getRowHeight() / 2f - editor.getOffsetY(), paintOther);
                    }

                    // Draw underline
                    var underlineColor = span.getUnderlineColor();
                    int underlineColorInt;
                    if (underlineColor != null && (underlineColorInt = underlineColor.resolve(editor.getColorScheme())) != 0) {
                        tmpRect.bottom = editor.getRowBottom(row) - editor.getOffsetY() - editor.getDpUnit() * 1;
                        tmpRect.top = tmpRect.bottom - editor.getRowHeight() * RenderingConstants.TEXT_UNDERLINE_WIDTH_FACTOR;
                        tmpRect.left = paintingOffset;
                        tmpRect.right = paintingOffset + width;
                        drawColor(canvas, underlineColorInt, tmpRect);
                    }

                    // Invoke external renderer postDraw
                    if (renderer != null && renderer.requirePostDraw()) {
                        int saveCount = canvas.save();
                        canvas.translate(paintingOffset, editor.getRowTop(row) - editor.getOffsetY());
                        canvas.clipRect(0f, 0f, width, editor.getRowHeight());
                        try {
                            renderer.draw(canvas, paintGeneral, editor.getColorScheme(), false);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Error while invoking external renderer", e);
                        }
                        canvas.restoreToCount(saveCount);
                    }

                    paintingOffset += width;

                    if (paintEnd >= lastVisibleChar || paintEnd >= columnCount) {
                        break;
                    }
                    spanOffset++;
                    if (spanOffset < reader.getSpanCount()) {
                        span = reader.getSpanAt(spanOffset);
                    } else {
                        spanOffset--;
                        //break;
                    }
                }

                // Draw hard wrap
                if (lastVisibleChar == columnCount && (nonPrintableFlags & CodeEditor.FLAG_DRAW_LINE_SEPARATOR) != 0) {
                    drawMiniGraph(canvas, paintingOffset, row, "↵");
                }
            } else {
                paintingOffset = offset + editor.getRenderContext().getRenderNodeHolder().drawLineHardwareAccelerated(canvas, line, offset, editor.getRowTop(line) - editor.getOffsetY()) - editor.getDpUnit() * 20;
                lastVisibleChar = columnCount;
            }

            // Recover the offset
            paintingOffset = backupOffset;

            // Draw non-printable characters
            if (circleRadius != 0f && (leadingWhitespaceEnd != columnCount || (nonPrintableFlags & CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE) != 0)) {
                if ((nonPrintableFlags & CodeEditor.FLAG_DRAW_WHITESPACE_LEADING) != 0) {
                    drawWhitespaces(canvas, paintingOffset, line, row, firstVisibleChar, lastVisibleChar, 0, leadingWhitespaceEnd);
                }
                if ((nonPrintableFlags & CodeEditor.FLAG_DRAW_WHITESPACE_INNER) != 0) {
                    drawWhitespaces(canvas, paintingOffset, line, row, firstVisibleChar, lastVisibleChar, leadingWhitespaceEnd, trailingWhitespaceStart);
                }
                if ((nonPrintableFlags & CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING) != 0) {
                    drawWhitespaces(canvas, paintingOffset, line, row, firstVisibleChar, lastVisibleChar, trailingWhitespaceStart, columnCount);
                }
                if ((nonPrintableFlags & CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION) != 0 && cursor.isSelected() && line >= cursor.getLeftLine() && line <= cursor.getRightLine()) {
                    int selectionStart = 0;
                    int selectionEnd = columnCount;
                    if (line == cursor.getLeftLine()) {
                        selectionStart = cursor.getLeftColumn();
                    }
                    if (line == cursor.getRightLine()) {
                        selectionEnd = cursor.getRightColumn();
                    }
                    if ((nonPrintableFlags & 0b1110) == 0) {
                        drawWhitespaces(canvas, paintingOffset, line, row, firstVisibleChar, lastVisibleChar, selectionStart, selectionEnd);
                    } else {
                        if ((nonPrintableFlags & CodeEditor.FLAG_DRAW_WHITESPACE_LEADING) == 0) {
                            drawWhitespaces(canvas, paintingOffset, line, row, firstVisibleChar, lastVisibleChar, selectionStart, Math.min(leadingWhitespaceEnd, selectionEnd));
                        }
                        if ((nonPrintableFlags & CodeEditor.FLAG_DRAW_WHITESPACE_INNER) == 0) {
                            drawWhitespaces(canvas, paintingOffset, line, row, firstVisibleChar, lastVisibleChar, Math.max(leadingWhitespaceEnd, selectionStart), Math.min(trailingWhitespaceStart, selectionEnd));
                        }
                        if ((nonPrintableFlags & CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING) == 0) {
                            drawWhitespaces(canvas, paintingOffset, line, row, firstVisibleChar, lastVisibleChar, Math.max(trailingWhitespaceStart, selectionStart), selectionEnd);
                        }
                    }
                }
            }

            // Draw composing text underline
            if (composingPosition != null && line == composingPosition.line) {
                int composingStart = composingPosition.column;
                int composingEnd = composingStart + composingLength;
                int paintStart = Math.min(Math.max(composingStart, firstVisibleChar), lastVisibleChar);
                int paintEnd = Math.min(Math.max(composingEnd, firstVisibleChar), lastVisibleChar);
                if (paintStart < paintEnd) {
                    tmpRect.top = editor.getRowBottom(row) - editor.getOffsetY();
                    tmpRect.bottom = tmpRect.top + editor.getRowHeight() * 0.06f;
                    tmpRect.left = paintingOffset + measureText(lineBuf, line, firstVisibleChar, paintStart - firstVisibleChar);
                    tmpRect.right = tmpRect.left + measureText(lineBuf, line, paintStart, paintEnd - paintStart);
                    drawColor(canvas, editor.getColorScheme().getColor(EditorColorScheme.UNDERLINE), tmpRect);
                }
            }

            final var layout = editor.getLayout();
            // Draw cursors
            if (cursor.isSelected()) {
                if (cursor.getLeftLine() == line && isInside(cursor.getLeftColumn(), rowInf.startColumn, rowInf.endColumn, line)) {
                    float centerX = editor.measureTextRegionOffset() + layout.getCharLayoutOffset(cursor.getLeftLine(), cursor.getLeftColumn())[1] - editor.getOffsetX();
                    var type = content.isRtlAt(cursor.getLeftLine(), cursor.getLeftColumn()) ? SelectionHandleStyle.HANDLE_TYPE_RIGHT : SelectionHandleStyle.HANDLE_TYPE_LEFT;
                    postDrawCursor.add(new DrawCursorTask(centerX, getRowBottomForBackground(row) - editor.getOffsetY(), type, editor.getLeftHandleDescriptor()));
                }
                if (cursor.getRightLine() == line && isInside(cursor.getRightColumn(), rowInf.startColumn, rowInf.endColumn, line)) {
                    float centerX = editor.measureTextRegionOffset() + layout.getCharLayoutOffset(cursor.getRightLine(), cursor.getRightColumn())[1] - editor.getOffsetX();
                    var type = content.isRtlAt(cursor.getRightLine(), cursor.getRightColumn()) ? SelectionHandleStyle.HANDLE_TYPE_LEFT : SelectionHandleStyle.HANDLE_TYPE_RIGHT;
                    postDrawCursor.add(new DrawCursorTask(centerX, getRowBottomForBackground(row) - editor.getOffsetY(), type, editor.getRightHandleDescriptor()));
                }
            } else if (cursor.getLeftLine() == line && isInside(cursor.getLeftColumn(), rowInf.startColumn, rowInf.endColumn, line)) {
                float centerX = editor.measureTextRegionOffset() + layout.getCharLayoutOffset(cursor.getLeftLine(), cursor.getLeftColumn())[1] - editor.getOffsetX();
                postDrawCursor.add(new DrawCursorTask(centerX, getRowBottomForBackground(row) - editor.getOffsetY(), SelectionHandleStyle.HANDLE_TYPE_INSERT, editor.getInsertHandleDescriptor()));
            }
            // Draw dragging selection or selecting target
            if (draggingSelection != null) {
                if (draggingSelection.line == line && isInside(draggingSelection.column, rowInf.startColumn, rowInf.endColumn, line)) {
                    float centerX = editor.measureTextRegionOffset() + layout.getCharLayoutOffset(draggingSelection.line, draggingSelection.column)[1] - editor.getOffsetX();
                    postDrawCursor.add(new DrawCursorTask(centerX, getRowBottomForBackground(row) - editor.getOffsetY(), SelectionHandleStyle.HANDLE_TYPE_UNDEFINED, null));
                }
            } else if (editor.isInMouseMode() && editor.isTextSelected()) {
                var target = editor.getSelectingTarget();
                if (target != null && target.line == line && isInside(target.column, rowInf.startColumn, rowInf.endColumn, line)) {
                    float centerX = editor.measureTextRegionOffset() + layout.getCharLayoutOffset(target.line, target.column)[1] - editor.getOffsetX();
                    postDrawCursor.add(new DrawCursorTask(centerX, getRowBottomForBackground(row) - editor.getOffsetY(), SelectionHandleStyle.HANDLE_TYPE_UNDEFINED, null));
                }
            }

        }

        // Release last used reader object
        if (reader != null) {
            try {
                reader.moveToLine(-1);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to release SpanReader", e);
            }
        }

        paintGeneral.setFakeBoldText(false);
        paintGeneral.setTextSkewX(0);
        paintOther.setStrokeWidth(circleRadius * 2);
        bufferedDrawPoints.commitPoints(canvas, paintOther);
    }

    protected void drawDiagnosticIndicators(Canvas canvas, float offset) {
        var diagnosticsContainer = editor.getDiagnostics();
        var style = editor.getDiagnosticIndicatorStyle();
        if (diagnosticsContainer != null && style != DiagnosticIndicatorStyle.NONE && style != null) {
            var text = content;
            var firstVisRow = editor.getFirstVisibleRow();
            var lastVisRow = editor.getLastVisibleRow();
            var firstIndex = text.getCharIndex(editor.getFirstVisibleLine(), 0);
            var lastLine = Math.min(text.getLineCount() - 1, editor.getLastVisibleLine() + 1);
            var lastIndex = text.getCharIndex(lastLine, 0) + text.getColumnCount(lastLine);
            diagnosticsContainer.queryInRegion(collectedDiagnostics, firstIndex, lastIndex);
            if (collectedDiagnostics.isEmpty()) {
                return;
            }
            final float waveLength = editor.getDpUnit() * editor.getProps().indicatorWaveLength;
            final float amplitude = editor.getDpUnit() * editor.getProps().indicatorWaveAmplitude;
            final float waveWidth = editor.getDpUnit() * editor.getProps().indicatorWaveWidth;
            var start = new CharPosition();
            var end = new CharPosition();
            var indexer = cursor.getIndexer();
            for (var region : collectedDiagnostics) {
                var startIndex = Math.max(firstIndex, region.startIndex);
                var endIndex = Math.min(lastIndex, region.endIndex);
                indexer.getCharPosition(startIndex, start);
                indexer.getCharPosition(endIndex, end);
                var startRow = editor.getLayout().getRowIndexForPosition(startIndex);
                var endRow = editor.getLayout().getRowIndexForPosition(endIndex);
                // Setup color
                var colorId = (region.severity >= 0 && region.severity <= 3) ? sDiagnosticsColorMapping[region.severity] : 0;
                if (colorId == 0) {
                    break;
                }
                paintOther.setColor(editor.getColorScheme().getColor(colorId));
                startRow = Math.max(firstVisRow, startRow);
                endRow = Math.min(lastVisRow, endRow);
                for (int i = startRow; i <= endRow; i++) {
                    var row = editor.getLayout().getRowAt(i);
                    var startX = 0f;
                    if (i == startRow) {
                        startX = measureText(getLine(row.lineIndex), row.lineIndex, row.startColumn, Math.max(start.column - row.startColumn, 0));
                    }
                    float endX;
                    if (i != endRow) {
                        endX = measureText(getLine(row.lineIndex), row.lineIndex, row.startColumn, row.endColumn - row.startColumn);
                    } else {
                        endX = measureText(getLine(row.lineIndex), row.lineIndex, row.startColumn, Math.max(0, end.column - row.startColumn));
                    }
                    startX += offset;
                    endX += offset;
                    // Make it always visible
                    if (Math.abs(startX - endX) < 1e-2) {
                        endX = startX + paintGeneral.measureText("a");
                    }
                    if (endX > 0 && startX < editor.getWidth()) {
                        // Draw
                        float centerY = editor.getRowBottom(i) - editor.getOffsetY();
                        switch (style) {
                            case WAVY_LINE: {
                                var lineWidth = 0 - startX;
                                var waveCount = (int) Math.ceil(lineWidth / waveLength);
                                var phi = lineWidth < 0 ? 0f : (waveLength * waveCount - lineWidth);
                                lineWidth = endX - startX;
                                canvas.save();
                                canvas.clipRect(startX, 0, endX, canvas.getHeight());
                                canvas.translate(startX, centerY);
                                tmpPath.reset();
                                tmpPath.moveTo(0, 0);
                                waveCount = (int) Math.ceil((phi + lineWidth) / waveLength);
                                for (int j = 0; j < waveCount; j++) {
                                    tmpPath.quadTo(waveLength * j + waveLength / 4, amplitude, waveLength * j + waveLength / 2, 0);
                                    tmpPath.quadTo(waveLength * j + waveLength * 3 / 4, -amplitude, waveLength * j + waveLength, 0);
                                }
                                // Draw path
                                paintOther.setStrokeWidth(waveWidth);
                                paintOther.setStyle(Paint.Style.STROKE);
                                canvas.drawPath(tmpPath, paintOther);
                                canvas.restore();
                                paintOther.setStyle(Paint.Style.FILL);
                                break;
                            }
                            case LINE: {
                                paintOther.setStrokeWidth(waveWidth);
                                canvas.drawLine(startX, centerY, endX, centerY, paintOther);
                                break;
                            }
                            case DOUBLE_LINE: {
                                paintOther.setStrokeWidth(waveWidth / 3f);
                                canvas.drawLine(startX, centerY, endX, centerY, paintOther);
                                canvas.drawLine(startX, centerY - waveWidth, endX, centerY - waveWidth, paintOther);
                                break;
                            }
                        }
                    }
                }
            }
        }
        collectedDiagnostics.clear();
    }

    /**
     * Draw non-printable characters
     */
    protected void drawWhitespaces(Canvas canvas, float offset, int line, int row, int rowStart, int rowEnd, int min, int max) {
        int paintStart = Math.max(rowStart, Math.min(rowEnd, min));
        int paintEnd = Math.max(rowStart, Math.min(rowEnd, max));
        paintOther.setColor(editor.getColorScheme().getColor(EditorColorScheme.NON_PRINTABLE_CHAR));

        if (paintStart < paintEnd) {
            float spaceWidth = paintGeneral.getSpaceWidth();
            float rowCenter = (editor.getRowTop(row) + editor.getRowBottom(row)) / 2f - editor.getOffsetY();
            offset += measureText(lineBuf, line, rowStart, paintStart - rowStart);
            var chars = lineBuf.getBackingCharArray();
            var lastPos = paintStart;
            while (paintStart < paintEnd) {
                char ch = chars[paintStart];
                int paintCount = 0;
                boolean paintLine = false;
                if (ch == ' ' || ch == '\t') {
                    offset += measureText(lineBuf, line, lastPos, paintStart - lastPos);
                }
                if (ch == ' ') {
                    paintCount = 1;
                } else if (ch == '\t') {
                    if ((editor.getNonPrintablePaintingFlags() & CodeEditor.FLAG_DRAW_TAB_SAME_AS_SPACE) != 0) {
                        paintCount = editor.getTabWidth();
                    } else {
                        paintLine = true;
                    }
                }
                for (int i = 0; i < paintCount; i++) {
                    float charStartOffset = offset + spaceWidth * i;
                    float charEndOffset = charStartOffset + spaceWidth;
                    float centerOffset = (charStartOffset + charEndOffset) / 2f;
                    bufferedDrawPoints.drawPoint(centerOffset, rowCenter);
                }
                if (paintLine) {
                    var charWidth = editor.getTabWidth() * spaceWidth;
                    float delta = charWidth * 0.05f;
                    canvas.drawLine(offset + delta, rowCenter, offset + charWidth - delta, rowCenter, paintOther);
                }

                if (ch == ' ' || ch == '\t') {
                    offset += (ch == ' ' ? spaceWidth : spaceWidth * editor.getTabWidth());
                    lastPos = paintStart + 1;
                }
                paintStart++;
            }
        }
    }

    /**
     * Draw small characters as graph
     */
    protected void drawMiniGraph(Canvas canvas, float offset, int row, String graph) {
        // Draw
        paintGraph.setColor(editor.getColorScheme().getColor(EditorColorScheme.NON_PRINTABLE_CHAR));
        float baseline = row == -1 ? (editor.getRowBottom(0) - metricsGraph.descent) : (editor.getRowBottom(row) - editor.getOffsetY() - metricsGraph.descent);
        canvas.drawText(graph, 0, graph.length(), offset, baseline, paintGraph);
    }

    protected int getRowTopForBackground(int row) {
        if (!editor.getProps().textBackgroundWrapTextOnly) {
            return editor.getRowTop(row);
        } else {
            return editor.getRowTopOfText(row);
        }
    }

    protected int getRowBottomForBackground(int row) {
        if (!editor.getProps().textBackgroundWrapTextOnly) {
            return editor.getRowBottom(row);
        } else {
            return editor.getRowBottomOfText(row);
        }
    }

    /**
     * Draw background of a text region
     *
     * @param canvas         Canvas to draw
     * @param row            The row index
     * @param highlightStart Region start
     * @param highlightEnd   Region end
     * @param color          Color of background
     */
    protected void drawRowRegionBackground(Canvas canvas, int row, int line, int highlightStart, int highlightEnd, int rowStart, int rowEnd, int color) {
        highlightStart = Math.max(highlightStart, rowStart);
        highlightEnd = Math.min(highlightEnd, rowEnd);
        if (highlightStart < highlightEnd) {
            tmpRect.top = getRowTopForBackground(row) - editor.getOffsetY();
            tmpRect.bottom = getRowBottomForBackground(row) - editor.getOffsetY();
            var dirs = getLineDirections(line);
            var lineObj = getLine(line);
            var empty = true;
            paintGeneral.setColor(color);
            float paintingOffset = editor.measureTextRegionOffset() - editor.getOffsetX();
            for (int i = 0; i < dirs.getRunCount(); i++) {
                int sharedStart = Math.max(highlightStart, dirs.getRunStart(i));
                int sharedEnd = Math.min(highlightEnd, dirs.getRunEnd(i));
                if (dirs.getRunStart(i) >= highlightEnd) {
                    break;
                }
                var measureStart = Math.max(rowStart, dirs.getRunStart(i));
                var measureEnd = Math.min(rowEnd, dirs.getRunEnd(i));
                var runWidth = measureEnd <= measureStart ? 0f : measureText(lineObj, line, measureStart, measureEnd - measureStart);
                if (sharedStart >= sharedEnd) {
                    paintingOffset += runWidth;
                    continue;
                }
                var rtl = dirs.isRunRtl(i);
                float left, right;
                if (rtl) {
                    left = paintingOffset + runWidth - measureText(lineObj, line, measureStart, sharedStart - measureStart);
                    right = paintingOffset + runWidth - measureText(lineObj, line, measureStart, sharedEnd - measureStart);
                } else {
                    left = paintingOffset + measureText(lineObj, line, measureStart, sharedStart - measureStart);
                    right = paintingOffset + measureText(lineObj, line, measureStart, sharedEnd - measureStart);
                }
                if (left > right) {
                    var tmp = left;
                    left = right;
                    right = tmp;
                }
                if (empty) {
                    tmpRect.left = left;
                    tmpRect.right = right;
                    empty = false;
                } else {
                    if (Math.abs(left - tmpRect.right) < 1e-2) {
                        tmpRect.right = right;
                    } else if (Math.abs(right - tmpRect.left) < 1e-2) {
                        tmpRect.left = left;
                    } else {
                        drawRowBackgroundRect(canvas, tmpRect);
                        tmpRect.left = left;
                        tmpRect.right = right;
                    }
                }
                paintingOffset += runWidth;
            }
            if (!empty) {
                drawRowBackgroundRect(canvas, tmpRect);
            }
        }
    }

    protected void drawRowBackgroundRect(Canvas canvas, RectF rect) {
        if (editor.getProps().enableRoundTextBackground) {
            canvas.drawRoundRect(rect, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, paintGeneral);
        } else {
            canvas.drawRect(rect, paintGeneral);
        }
    }

    /**
     * Draw text region with highlighting selected text
     *
     * @param canvas      Canvas to draw
     * @param offsetX     Start paint offset x on canvas
     * @param baseline    Baseline on canvas
     * @param line        Drawing line index
     * @param startIndex  Start index to paint
     * @param endIndex    Index of end character to paint
     * @param columnCount Column count of line
     * @param color       Color of normal text in this region
     */
    protected void drawRegionText(Canvas canvas, float offsetX, float baseline, int line, int startIndex, int endIndex, int contextStart, int contextEnd, boolean isRtl, int columnCount, int color) {
        boolean hasSelectionOnLine = cursor.isSelected() && line >= cursor.getLeftLine() && line <= cursor.getRightLine();
        int selectionStart = 0;
        int selectionEnd = columnCount;
        int contextCount = contextEnd - contextStart;
        if (line == cursor.getLeftLine()) {
            selectionStart = cursor.getLeftColumn();
        }
        if (line == cursor.getRightLine()) {
            selectionEnd = cursor.getRightColumn();
        }
        paintGeneral.setColor(color);
        if (hasSelectionOnLine && editor.getColorScheme().getColor(EditorColorScheme.TEXT_SELECTED) != 0) {
            if (endIndex <= selectionStart || startIndex >= selectionEnd) {
                drawText(canvas, lineBuf, startIndex, endIndex - startIndex, contextStart, contextCount, isRtl, offsetX, baseline, line);
            } else {
                if (startIndex <= selectionStart) {
                    if (endIndex >= selectionEnd) {
                        //Three regions
                        //startIndex - selectionStart
                        drawText(canvas, lineBuf, startIndex, selectionStart - startIndex, contextStart, contextCount, isRtl, offsetX, baseline, line);
                        float deltaX = measureText(lineBuf, line, startIndex, selectionStart - startIndex);
                        //selectionStart - selectionEnd
                        paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.TEXT_SELECTED));
                        drawText(canvas, lineBuf, selectionStart, selectionEnd - selectionStart, contextStart, contextCount, isRtl, offsetX + deltaX, baseline, line);
                        deltaX += measureText(lineBuf, line, selectionStart, selectionEnd - selectionStart);
                        //selectionEnd - endIndex
                        paintGeneral.setColor(color);
                        drawText(canvas, lineBuf, selectionEnd, endIndex - selectionEnd, contextStart, contextCount, isRtl, offsetX + deltaX, baseline, line);
                    } else {
                        //Two regions
                        //startIndex - selectionStart
                        drawText(canvas, lineBuf, startIndex, selectionStart - startIndex, contextStart, contextCount, isRtl, offsetX, baseline, line);
                        //selectionStart - endIndex
                        paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.TEXT_SELECTED));
                        drawText(canvas, lineBuf, selectionStart, endIndex - selectionStart, contextStart, contextCount, isRtl, offsetX + measureText(lineBuf, line, startIndex, selectionStart - startIndex), baseline, line);
                    }
                } else {
                    //selectionEnd > startIndex > selectionStart
                    if (endIndex > selectionEnd) {
                        //Two regions
                        //selectionEnd - endIndex
                        drawText(canvas, lineBuf, selectionEnd, endIndex - selectionEnd, contextStart, contextCount, isRtl, offsetX + measureText(lineBuf, line, startIndex, selectionEnd - startIndex), baseline, line);
                        //startIndex - selectionEnd
                        paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.TEXT_SELECTED));
                        drawText(canvas, lineBuf, startIndex, selectionEnd - startIndex, contextStart, contextCount, isRtl, offsetX, baseline, line);
                    } else {
                        //One region
                        paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.TEXT_SELECTED));
                        drawText(canvas, lineBuf, startIndex, endIndex - startIndex, contextStart, contextCount, isRtl, offsetX, baseline, line);
                    }
                }
            }
        } else {
            drawText(canvas, lineBuf, startIndex, endIndex - startIndex, contextStart, contextCount, isRtl, offsetX, baseline, line);
        }
    }

    protected void drawRegionTextDirectional(Canvas canvas, float offsetX, float baseline, int line, int startIndex, int endIndex, int contextStart, int contextEnd, int columnCount, int color) {
        var directions = getLineDirections(line);
        var width = 0f;
        for (int i = 0; i < directions.getRunCount(); i++) {
            int sharedStart = Math.max(directions.getRunStart(i), startIndex);
            int sharedEnd = Math.min(directions.getRunEnd(i), endIndex);
            if (sharedEnd > sharedStart) {
                drawRegionText(canvas, offsetX + width, baseline, line, sharedStart, sharedEnd, contextStart, contextEnd, directions.isRunRtl(i), columnCount, color);
            }
            if (i + 1 < directions.getRunCount() && sharedEnd > sharedStart) {
                width += measureText(getLine(line), line, sharedStart, sharedEnd - sharedStart);
            }
        }
    }

    protected void drawFunctionCharacter(Canvas canvas, float offsetX, float offsetY, float width, char ch) {
        paintGraph.setTextAlign(android.graphics.Paint.Align.CENTER);
        float topY = offsetY - editor.getRowBaseline(0);
        float heightOrigin = editor.getRowHeight();
        float heightScaled = metricsGraph.descent - metricsGraph.ascent;
        float centerY = topY + heightOrigin / 2f;
        float baseline = centerY - heightScaled / 2f - metricsGraph.ascent;
        paintGraph.setColor(paintGeneral.getColor());
        canvas.drawText(FunctionCharacters.getNameForFunctionCharacter(ch), offsetX + width / 2f, baseline, paintGraph);
        paintGraph.setTextAlign(android.graphics.Paint.Align.LEFT);
        float actualWidth = paintGraph.measureText(FunctionCharacters.getNameForFunctionCharacter(ch));
        tmpRect.top = centerY - heightScaled / 2f;
        tmpRect.bottom = centerY + heightScaled / 2f;
        tmpRect.left = offsetX + width / 2f - actualWidth / 2f;
        tmpRect.right = offsetX + width / 2f + actualWidth / 2f;
        int color = paintGeneral.getColor();
        paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.FUNCTION_CHAR_BACKGROUND_STROKE));
        paintGeneral.setStyle(android.graphics.Paint.Style.STROKE);
        paintGeneral.setStrokeWidth(editor.getRowHeightOfText() * 0.05f);
        drawRowBackgroundRect(canvas, tmpRect);
        paintGeneral.setStyle(android.graphics.Paint.Style.FILL);
        paintGeneral.setColor(color);
    }

    /**
     * Draw text on the given position
     *
     * @param canvas Canvas to draw
     * @param line   Source of characters
     * @param index  The index in array
     * @param count  Count of characters
     * @param offX   Offset x for paint
     * @param offY   Offset y for paint(baseline)
     */
    protected void drawText(Canvas canvas, ContentLine line, int index, int count, int contextStart, int contextCount, boolean isRtl, float offX, float offY, int lineNumber) {
        int end = index + count;
        var src = line.getBackingCharArray();
        end = Math.min(src.length, end);
        int st = index;
        var renderFuncChars = editor.isRenderFunctionCharacters();
        for (int i = index; i < end; i++) {
            if (src[i] == '\t') {
                drawTextRunDirect(canvas, src, st, i - st, contextStart, contextCount, offX, offY, isRtl);
                offX = offX + measureText(line, lineNumber, st, i - st + 1);
                st = i + 1;
            } else if (renderFuncChars && FunctionCharacters.isEditorFunctionChar(src[i])) {
                drawTextRunDirect(canvas, src, st, i - st, contextStart, contextCount, offX, offY, isRtl);
                offX = offX + measureText(line, lineNumber, st, i - st);
                float width = measureText(line, lineNumber, i, 1);
                drawFunctionCharacter(canvas, offX, offY, width, src[i]);
                offX = offX + width;
                st = i + 1;
            }
        }
        if (st < end) {
            drawTextRunDirect(canvas, src, st, end - st, contextStart, contextCount, offX, offY, isRtl);
        }
    }

    @SuppressLint("NewApi")
    protected void drawTextRunDirect(Canvas canvas, char[] src, int index, int count, int contextStart, int contextCount, float offX, float offY, boolean isRtl) {
        if (basicDisplayMode) {
            int charCount;
            for (int i = 0; i < count; i += charCount) {
                charCount = 1;
                if (Character.isHighSurrogate(src[index + i]) && i + 1 < count && Character.isLowSurrogate(src[index + i + 1])) {
                    charCount = 2;
                }
                canvas.drawText(src, index + i, charCount, offX, offY, paintGeneral);
                offX += paintGeneral.myGetTextRunAdvances(src, index + i, charCount, index + i, charCount, false, null, 0, true);
            }
        } else {
            canvas.drawTextRun(src, index, count, contextStart, contextCount, offX, offY, isRtl, paintGeneral);
        }
    }

    /**
     * Is inside the region
     *
     * @param index Index to test
     * @param start Start of region
     * @param end   End of region
     * @param line  Checking line
     * @return true if cursor should be drawn in this row
     */
    private boolean isInside(int index, int start, int end, int line) {
        // Due not to draw duplicate cursors for a single one
        if (index == end && content.getLine(line).length() != end) {
            return false;
        }
        return index >= start && index <= end;
    }

    public long getTimestamp() {
        return displayTimestamp;
    }

    public android.graphics.Paint.FontMetricsInt getLineNumberMetrics() {
        return metricsLineNumber;
    }

    /**
     * Draw effect of edges
     *
     * @param canvas The canvas to draw
     */
    protected void drawEdgeEffect(Canvas canvas) {
        boolean postDraw = false;
        var verticalEdgeEffect = editor.getVerticalEdgeEffect();
        var horizontalEdgeEffect = editor.getHorizontalEdgeEffect();
        if (!verticalEdgeEffect.isFinished()) {
            boolean bottom = editor.getEventHandler().glowTopOrBottom;
            if (bottom) {
                canvas.save();
                canvas.translate(-editor.getMeasuredWidth(), editor.getMeasuredHeight());
                canvas.rotate(180, editor.getMeasuredWidth(), 0);
            }
            postDraw = verticalEdgeEffect.draw(canvas);
            if (bottom) {
                canvas.restore();
            }
        }
        if (editor.isWordwrap()) {
            horizontalEdgeEffect.finish();
        }
        if (!horizontalEdgeEffect.isFinished()) {
            canvas.save();
            boolean right = editor.getEventHandler().glowLeftOrRight;
            if (right) {
                canvas.rotate(90);
                canvas.translate(0, -editor.getMeasuredWidth());
            } else {
                canvas.translate(0, editor.getMeasuredHeight());
                canvas.rotate(-90);
            }
            postDraw = horizontalEdgeEffect.draw(canvas) || postDraw;
            canvas.restore();
        }
        var scroller = editor.getScroller();
        if (scroller.isOverScrolled()) {
            if (verticalEdgeEffect.isFinished() && (scroller.getCurrY() < 0 || scroller.getCurrY() > editor.getScrollMaxY())) {
                editor.getEventHandler().glowTopOrBottom = scroller.getCurrY() >= editor.getScrollMaxY();
                verticalEdgeEffect.onAbsorb((int) scroller.getCurrVelocity());
                postDraw = true;
            }
            if (horizontalEdgeEffect.isFinished() && (scroller.getCurrX() < 0 || scroller.getCurrX() > editor.getScrollMaxX())) {
                editor.getEventHandler().glowLeftOrRight = scroller.getCurrX() >= editor.getScrollMaxX();
                horizontalEdgeEffect.onAbsorb((int) scroller.getCurrVelocity());
                postDraw = true;
            }
        }
        if (postDraw) {
            editor.postInvalidate();
        }
    }

    /**
     * Draw code block lines on screen
     *
     * @param canvas  The canvas to draw
     * @param offsetX The start x offset for text
     */
    protected void drawBlockLines(Canvas canvas, float offsetX) {
        final var styles = editor.getStyles();
        List<CodeBlock> blocks = styles == null ? null : styles.blocks;
        var indentMode = styles != null && styles.isIndentCountMode();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        int first = editor.getFirstVisibleRow();
        int last = editor.getLastVisibleRow();
        boolean mark = false;
        int invalidCount = 0;
        int maxCount = styles.getSuppressSwitch();
        int mm = editor.binarySearchEndBlock(first, blocks);
        if (mm == -1) {
            mm = 0;
        }
        int cursorIdx = editor.getCurrentCursorBlock();
        for (int curr = mm; curr < blocks.size(); curr++) {
            CodeBlock block = blocks.get(curr);
            if (block == null) {
                continue;
            }
            if (CodeEditor.hasVisibleRegion(block.startLine, block.endLine, first, last)) {
                try {
                    var lineContent = getLine(block.endLine);
                    float offsetEnd = indentMode ? paintGeneral.getSpaceWidth() * block.endColumn : measureText(lineContent, block.endLine, 0, Math.min(block.endColumn, lineContent.length()));
                    lineContent = getLine(block.startLine);
                    float offsetStart = indentMode ? paintGeneral.getSpaceWidth() * block.startColumn : measureText(lineContent, block.startLine, 0, Math.min(block.startColumn, lineContent.length()));
                    float offset = Math.min(offsetEnd, offsetStart);
                    float centerX = offset + offsetX;
                    tmpRect.top = Math.max(0, editor.getRowBottom(block.startLine) - editor.getOffsetY());
                    tmpRect.bottom = Math.min(editor.getHeight(), (block.toBottomOfEndLine ? editor.getRowBottom(block.endLine) : editor.getRowTop(block.endLine)) - editor.getOffsetY());
                    tmpRect.left = centerX - editor.getDpUnit() * editor.getBlockLineWidth() / 2;
                    tmpRect.right = centerX + editor.getDpUnit() * editor.getBlockLineWidth() / 2;
                    drawColor(canvas, editor.getColorScheme().getColor(curr == cursorIdx ? EditorColorScheme.BLOCK_LINE_CURRENT : EditorColorScheme.BLOCK_LINE), tmpRect);
                } catch (IndexOutOfBoundsException e) {
                    // Ignored
                    // Because the exception usually occurs when the content is changed.
                }
                mark = true;
            } else if (mark) {
                if (invalidCount >= maxCount) break;
                invalidCount++;
            }
        }
    }

    protected void drawSideBlockLine(Canvas canvas) {
        if (!editor.getProps().drawSideBlockLine) {
            return;
        }
        List<CodeBlock> blocks = editor.getStyles() == null ? null : editor.getStyles().blocks;
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        var current = editor.getCurrentCursorBlock();
        if (current >= 0 && current < blocks.size()) {
            var block = blocks.get(current);
            var layout = editor.getLayout();
            try {
                float top = layout.getCharLayoutOffset(block.startLine, block.startColumn)[0] - editor.getRowHeight() - editor.getOffsetY();
                float bottom = layout.getCharLayoutOffset(block.endLine, block.endColumn)[0] - editor.getOffsetY();
                float left = editor.measureLineNumber();
                float right = left + editor.getDividerMarginLeft();
                float center = (left + right) / 2 - editor.getOffsetX();
                paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.SIDE_BLOCK_LINE));
                paintGeneral.setStrokeWidth(editor.getDpUnit() * editor.getBlockLineWidth());
                canvas.drawLine(center, top, center, bottom, paintGeneral);
            } catch (IndexOutOfBoundsException e) {
                //ignored
            }
        }
    }

    /**
     * Draw scroll bars and tracks
     *
     * @param canvas The canvas to draw
     */
    protected void drawScrollBars(Canvas canvas) {
        verticalScrollBarRect.setEmpty();
        horizontalScrollBarRect.setEmpty();
        if (!editor.getEventHandler().shouldDrawScrollBarForTouch() && !(editor.isInMouseMode() && editor.getProps().mouseModeAlwaysShowScrollbars)) {
            return;
        }
        var percentage = editor.getEventHandler().getScrollBarFadeOutPercentageForTouch();
        if (editor.isInMouseMode() && editor.getProps().mouseModeAlwaysShowScrollbars) {
            percentage = 0f;
        }
        var size = editor.getDpUnit() * RenderingConstants.SCROLLBAR_WIDTH_DIP;
        if (editor.isHorizontalScrollBarEnabled() && !editor.isWordwrap() && editor.getScrollMaxX() > editor.getWidth() * 3 / 4) {
            canvas.save();
            canvas.translate(0f, size * percentage);

            drawScrollBarTrackHorizontal(canvas);
            drawScrollBarHorizontal(canvas);

            canvas.restore();
        }
        if (editor.isVerticalScrollBarEnabled() && editor.getScrollMaxY() > editor.getHeight() / 2) {
            canvas.save();
            canvas.translate(size * percentage, 0f);

            drawScrollBarTrackVertical(canvas);
            drawScrollBarVertical(canvas);

            canvas.restore();
        }
    }

    /**
     * Draw vertical scroll bar track
     *
     * @param canvas Canvas to draw
     */
    protected void drawScrollBarTrackVertical(Canvas canvas) {
        if (editor.getEventHandler().holdVerticalScrollBar()) {
            tmpRect.right = editor.getWidth();
            tmpRect.left = editor.getWidth() - editor.getDpUnit() * RenderingConstants.SCROLLBAR_WIDTH_DIP;
            tmpRect.top = 0;
            tmpRect.bottom = editor.getHeight();
            if (verticalScrollbarTrackDrawable != null) {
                verticalScrollbarTrackDrawable.setBounds((int) tmpRect.left, (int) tmpRect.top, (int) tmpRect.right, (int) tmpRect.bottom);
                verticalScrollbarTrackDrawable.draw(canvas);
            } else {
                drawColor(canvas, editor.getColorScheme().getColor(EditorColorScheme.SCROLL_BAR_TRACK), tmpRect);
            }
        }
    }

    /**
     * Draw vertical scroll bar
     *
     * @param canvas Canvas to draw
     */
    protected void drawScrollBarVertical(Canvas canvas) {
        int height = editor.getHeight();
        float all = editor.getScrollMaxY() + height;
        float length = Math.max(height / all * height, editor.getDpUnit() * RenderingConstants.SCROLLBAR_LENGTH_MIN_DIP);
        float topY = editor.getOffsetY() * 1.0f / editor.getScrollMaxY() * (height - length);
        if (editor.getEventHandler().holdVerticalScrollBar()) {
            drawLineInfoPanel(canvas, topY, length);
        }
        tmpRect.right = editor.getWidth();
        tmpRect.left = editor.getWidth() - editor.getDpUnit() * RenderingConstants.SCROLLBAR_WIDTH_DIP;
        tmpRect.top = topY;
        tmpRect.bottom = topY + length;
        verticalScrollBarRect.set(tmpRect);
        if (verticalScrollbarThumbDrawable != null) {
            verticalScrollbarThumbDrawable.setState(editor.getEventHandler().holdVerticalScrollBar() ? PRESSED_DRAWABLE_STATE : DEFAULT_DRAWABLE_STATE);
            verticalScrollbarThumbDrawable.setBounds((int) tmpRect.left, (int) tmpRect.top, (int) tmpRect.right, (int) tmpRect.bottom);
            verticalScrollbarThumbDrawable.draw(canvas);
        } else {
            drawColor(canvas, editor.getColorScheme().getColor(editor.getEventHandler().holdVerticalScrollBar() ? EditorColorScheme.SCROLL_BAR_THUMB_PRESSED : EditorColorScheme.SCROLL_BAR_THUMB), tmpRect);
        }
    }

    /**
     * Draw line number panel
     *
     * @param canvas Canvas to draw
     * @param topY   The y at the top of the vertical scrollbar
     * @param length The length of vertical scrollbar
     */
    protected void drawLineInfoPanel(Canvas canvas, float topY, float length) {
        if (!editor.isDisplayLnPanel()) {
            return;
        }
        int mode = editor.getLnPanelPositionMode();
        int position = editor.getLnPanelPosition();
        String text = editor.getLineNumberTipTextProvider().getCurrentText(editor);
        float backupSize = paintGeneral.getTextSize();
        paintGeneral.setTextSize(editor.getLineInfoTextSize());
        Paint.FontMetricsInt backupMetrics = metricsText;
        metricsText = paintGeneral.getFontMetricsInt();
        float expand = editor.getDpUnit() * 8;
        float textWidth = paintGeneral.measureText(text);
        float baseline;
        float textOffset = 0f;
        if (mode == LineInfoPanelPositionMode.FIXED) {
            tmpRect.top = editor.getHeight() / 2f - editor.getRowHeight() / 2f - expand;
            tmpRect.bottom = editor.getHeight() / 2f + editor.getRowHeight() / 2f + expand;
            tmpRect.left = editor.getWidth() / 2f - textWidth / 2f - expand;
            tmpRect.right = editor.getWidth() / 2f + textWidth / 2f + expand;
            baseline = editor.getHeight() / 2f + 2 * expand;
            float offset = 10 * editor.getDpUnit();
            if (position != LineInfoPanelPosition.CENTER) {
                if ((position | LineInfoPanelPosition.TOP) == position) {
                    tmpRect.top = offset;
                    tmpRect.bottom = offset + editor.getRowHeight() + 2 * expand;
                    baseline = offset + editor.getRowBaseline(0) + expand;
                }
                if ((position | LineInfoPanelPosition.BOTTOM) == position) {
                    tmpRect.top = editor.getHeight() - offset - 2 * expand - editor.getRowHeight();
                    tmpRect.bottom = editor.getHeight() - offset;
                    baseline = editor.getHeight() - editor.getRowHeight() + editor.getRowBaseline(0) - offset - expand;
                }
                if ((position | LineInfoPanelPosition.LEFT) == position) {
                    tmpRect.left = offset;
                    tmpRect.right = offset + 2 * expand + textWidth;
                }
                if ((position | LineInfoPanelPosition.RIGHT) == position) {
                    tmpRect.right = editor.getWidth() - offset;
                    tmpRect.left = editor.getWidth() - offset - expand * 2 - textWidth;
                }
            }
            drawColorRound(canvas, editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER_PANEL), tmpRect);
        } else {
            float[] radii = null;
            tmpRect.right = editor.getWidth() - 30 * editor.getDpUnit();
            tmpRect.left = editor.getWidth() - 30 * editor.getDpUnit() - expand * 2 - textWidth;
            if (position == LineInfoPanelPosition.TOP) {
                tmpRect.top = topY;
                tmpRect.bottom = topY + editor.getRowHeight() + 2 * expand;
                baseline = topY + editor.getRowBaseline(0) + expand;
                radii = new float[8];
                for (int i = 0; i < 8; i++) {
                    if (i != 5)
                        radii[i] = tmpRect.height() * RenderingConstants.ROUND_BUBBLE_FACTOR;
                }
            } else if (position == LineInfoPanelPosition.BOTTOM) {
                tmpRect.top = topY + length - editor.getRowHeight() - 2 * expand;
                tmpRect.bottom = topY + length;
                baseline = topY + length - editor.getRowBaseline(0) / 2f;
                radii = new float[8];
                for (int i = 0; i < 8; i++) {
                    if (i != 3)
                        radii[i] = tmpRect.height() * RenderingConstants.ROUND_BUBBLE_FACTOR;
                }
            } else {
                float centerY = topY + length / 2f;
                tmpRect.top = centerY - editor.getRowHeight() / 2f - expand;
                tmpRect.bottom = centerY + editor.getRowHeight() / 2f + expand;
                baseline = centerY - editor.getRowHeight() / 2f + editor.getRowBaseline(0);
            }
            if (radii != null) {
                tmpPath.reset();
                tmpPath.addRoundRect(tmpRect, radii, Path.Direction.CW);
            } else {
                tmpRect.offset(-expand, 0f);
                tmpRect.right += expand;
                textOffset = -expand / 2f;
                BubbleHelper.buildBubblePath(tmpPath, tmpRect);
            }
            paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER_PANEL));
            canvas.drawPath(tmpPath, paintGeneral);
        }
        float centerX = (tmpRect.left + tmpRect.right) / 2 + textOffset;
        paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT));
        paintGeneral.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, centerX, baseline, paintGeneral);
        paintGeneral.setTextAlign(Paint.Align.LEFT);
        paintGeneral.setTextSize(backupSize);
        metricsText = backupMetrics;
    }

    /**
     * Draw horizontal scroll bar track
     *
     * @param canvas Canvas to draw
     */
    protected void drawScrollBarTrackHorizontal(Canvas canvas) {
        if (editor.getEventHandler().holdHorizontalScrollBar()) {
            tmpRect.set(0, editor.getHeight() - editor.getDpUnit() * RenderingConstants.SCROLLBAR_WIDTH_DIP, editor.getWidth(), editor.getHeight());
            if (horizontalScrollbarTrackDrawable != null) {
                horizontalScrollbarTrackDrawable.setBounds((int) tmpRect.left, (int) tmpRect.top, (int) tmpRect.right, (int) tmpRect.bottom);
                horizontalScrollbarTrackDrawable.draw(canvas);
            } else {
                drawColor(canvas, editor.getColorScheme().getColor(EditorColorScheme.SCROLL_BAR_TRACK), tmpRect);
            }
        }
    }

    protected void patchSnippetRegions(Canvas canvas, float textOffset) {
        var controller = editor.getSnippetController();
        if (controller.isInSnippet()) {
            var editing = controller.getEditingTabStop();
            if (editing != null) {
                patchTextRegionWithColor(canvas, textOffset, editing.getStartIndex(), editing.getEndIndex(), 0, editor.getColorScheme().getColor(EditorColorScheme.SNIPPET_BACKGROUND_EDITING), 0);
            }
            for (SnippetItem snippetItem : controller.getEditingRelatedTabStops()) {
                patchTextRegionWithColor(canvas, textOffset, snippetItem.getStartIndex(), snippetItem.getEndIndex(), 0, editor.getColorScheme().getColor(EditorColorScheme.SNIPPET_BACKGROUND_RELATED), 0);
            }
            for (SnippetItem snippetItem : controller.getInactiveTabStops()) {
                patchTextRegionWithColor(canvas, textOffset, snippetItem.getStartIndex(), snippetItem.getEndIndex(), 0, editor.getColorScheme().getColor(EditorColorScheme.SNIPPET_BACKGROUND_INACTIVE), 0);
            }
        }
    }

    protected void patchHighlightedDelimiters(Canvas canvas, float textOffset) {
        if (editor.inputConnection.composingText.isComposing() || !editor.getProps().highlightMatchingDelimiters || editor.getCursor().isSelected()) {
            return;
        }
        var paired = editor.styleDelegate.getFoundBracketPair();
        if (paired != null) {
            var color = editor.getColorScheme().getColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND);
            var backgroundColor = editor.getColorScheme().getColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND);
            var underlineColor = editor.getColorScheme().getColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE);
            if (isInvalidTextBounds(paired.leftIndex, paired.leftLength) || isInvalidTextBounds(paired.rightIndex, paired.rightLength)) {
                // Index out of bounds
                return;
            }

            patchTextRegionWithColor(canvas, textOffset, paired.leftIndex, paired.leftIndex + paired.leftLength, color, backgroundColor, underlineColor);
            patchTextRegionWithColor(canvas, textOffset, paired.rightIndex, paired.rightIndex + paired.rightLength, color, backgroundColor, underlineColor);
        }
    }

    protected boolean isInvalidTextBounds(int index, int length) {
        return (index < 0 || length < 0 || index + length > content.length());
    }

    protected void patchTextRegionWithColor(Canvas canvas, float textOffset, int start, int end, int color, int backgroundColor, int underlineColor) {
        paintGeneral.setColor(color);
        paintOther.setStrokeWidth(editor.getRowHeightOfText() * RenderingConstants.MATCHING_DELIMITERS_UNDERLINE_WIDTH_FACTOR);
        paintGeneral.setStyle(android.graphics.Paint.Style.FILL_AND_STROKE);
        paintGeneral.setFakeBoldText(editor.getProps().boldMatchingDelimiters);
        var positions = getTextRegionPositions(start, end);
        patchTextRegions(canvas, textOffset, positions, (canvasLocal, horizontalOffset, row, line, startCol, endCol, style) -> {
            if (backgroundColor != 0) {
                tmpRect.top = getRowTopForBackground(row) - editor.getOffsetY();
                tmpRect.bottom = getRowBottomForBackground(row) - editor.getOffsetY();
                tmpRect.left = 0;
                tmpRect.right = editor.getWidth();
                paintOther.setColor(backgroundColor);
                if (editor.getProps().enableRoundTextBackground) {
                    canvas.drawRoundRect(tmpRect, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, editor.getRowHeight() * editor.getProps().roundTextBackgroundFactor, paintOther);
                } else {
                    canvas.drawRect(tmpRect, paintOther);
                }
            }
            if (color != 0) {
                paintGeneral.setTextSkewX(TextStyle.isItalics(style) ? RenderingConstants.TEXT_SKEW_X : 0f);
                paintGeneral.setStrikeThruText(TextStyle.isStrikeThrough(style));
                drawText(canvas, getLine(line), startCol, endCol - startCol, startCol, endCol - startCol, false, horizontalOffset, editor.getRowBaseline(row) - editor.getOffsetY(), line);
            }
            if (underlineColor != 0) {
                paintOther.setColor(underlineColor);
                var bottom = editor.getRowBottomOfText(row) - editor.getOffsetY() - editor.getRowHeightOfText() * 0.05f;
                canvas.drawLine(0, bottom, editor.getWidth(), bottom, paintOther);
            }
        });
        paintGeneral.setStyle(android.graphics.Paint.Style.FILL);
        paintGeneral.setFakeBoldText(false);
        paintGeneral.setTextSkewX(0f);
        paintGeneral.setStrikeThruText(false);
    }

    protected List<TextDisplayPosition> getTextRegionPositions(int start, int end) {
        var layout = editor.getLayout();
        var startRow = layout.getRowIndexForPosition(start);
        var endRow = layout.getRowIndexForPosition(end);
        var posStart = cursor.getIndexer().getCharPosition(start);
        var posEnd = cursor.getIndexer().getCharPosition(end);
        var itr = layout.obtainRowIterator(startRow, preloadedLines);
        var list = new ArrayList<TextDisplayPosition>();
        for (int i = startRow; i <= endRow && itr.hasNext(); i++) {
            var row = itr.next();
            var startOnRow = (i == startRow ? posStart.column : row.startColumn);
            var endOnRow = (i == endRow ? posEnd.column : row.endColumn);
            var position = new TextDisplayPosition();
            list.add(position);
            position.row = i;
            var line = content.getLine(row.lineIndex);
            position.left = measureText(line, row.lineIndex, row.startColumn, startOnRow - row.startColumn);
            position.right = position.left + measureText(line, row.lineIndex, startOnRow, endOnRow - startOnRow);
            position.startColumn = startOnRow;
            position.endColumn = endOnRow;
            position.line = row.lineIndex;
            position.rowStart = row.startColumn;
        }
        return list;
    }

    protected void patchTextRegions(Canvas canvas, float textOffset, List<TextDisplayPosition> positions, @NonNull PatchDraw patch) {
        var styles = editor.getStyles();
        var spans = styles != null ? styles.getSpans() : null;
        var reader = spans != null ? spans.read() : EmptyReader.getInstance();
        var firstVisRow = editor.getFirstVisibleRow();
        var lastVisRow = editor.getLastVisibleRow();
        for (var position : positions) {
            if (!(firstVisRow <= position.row && position.row <= lastVisRow)) {
                continue;
            }
            // First, get the line
            var line = position.line;
            try {
                reader.moveToLine(line);
            } catch (Exception e) {
                Log.e(LOG_TAG, "patchTextRegions: Unable to get spans", e);
                break;
            }
            var startCol = position.startColumn;
            var endCol = position.endColumn;
            var lineText = getLine(line);
            var column = lineText.length();
            canvas.save();
            var horizontalOffset = textOffset;
            boolean first = true;
            // Find spans to draw
            Span nextSpan = null;
            int spanCount = reader.getSpanCount();
            for (int i = 0; i < spanCount; i++) {
                Span span;
                if (nextSpan == null) {
                    span = reader.getSpanAt(i);
                } else {
                    span = nextSpan;
                }
                nextSpan = i + 1 == spanCount ? null : reader.getSpanAt(i + 1);
                var spanStart = Math.max(span.getColumn(), position.rowStart);
                var sharedStart = Math.max(startCol, spanStart);
                var spanEnd = nextSpan == null ? column : nextSpan.getColumn();
                spanEnd = Math.min(column, spanEnd); // Spans can be corrupted
                if (spanEnd <= position.startColumn) {
                    continue;
                }
                var sharedEnd = Math.min(endCol, spanEnd);
                if (sharedEnd - sharedStart > 0) {
                    // Clip canvas to patch the requested region
                    if (first) {
                        horizontalOffset += measureText(lineText, line, position.rowStart, spanStart - position.rowStart);
                        first = false;
                    }
                    if (TextStyle.isItalics(span.getStyleBits())) {
                        var path = new Path();
                        var y = editor.getRowBottomOfText(position.row) - editor.getOffsetY();
                        path.moveTo(textOffset + position.left, y);
                        path.lineTo(textOffset + position.left - RenderingConstants.TEXT_SKEW_X * y, 0f);
                        path.lineTo(editor.getWidth(), 0f);
                        path.lineTo(editor.getWidth(), editor.getHeight());
                        path.close();
                        canvas.clipPath(path);
                    } else {
                        canvas.clipRect(textOffset + position.left, 0, editor.getWidth(), editor.getHeight());
                    }

                    if (TextStyle.isItalics(span.getStyleBits())) {
                        var path = new Path();
                        var y = editor.getRowBottomOfText(position.row) - editor.getOffsetY();
                        path.moveTo(textOffset + position.right, y);
                        path.lineTo(textOffset + position.right - RenderingConstants.TEXT_SKEW_X * y, 0f);
                        path.lineTo(0, 0f);
                        path.lineTo(0, editor.getHeight());
                        path.close();
                        canvas.clipPath(path);
                    } else {
                        canvas.clipRect(0, 0, textOffset + position.right, editor.getHeight());
                    }

                    // Patch the text
                    patch.draw(canvas, horizontalOffset, position.row, line, spanStart, spanEnd, span.getStyle());
                }
                if (spanEnd >= endCol) {
                    break;
                }
                horizontalOffset += measureText(lineText, line, spanStart, spanEnd - spanStart);
            }
            canvas.restore();
        }
        try {
            reader.moveToLine(-1);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to release SpanReader", e);
        }
    }

    protected void drawSelectionOnAnimation(Canvas canvas) {
        if (!editor.isEditable()) {
            return;
        }
        tmpRect.bottom = editor.getCursorAnimator().animatedY() - editor.getOffsetY();
        tmpRect.top = tmpRect.bottom - (editor.getProps().textBackgroundWrapTextOnly ? editor.getRowHeightOfText() : editor.getRowHeight());
        float centerX = editor.getCursorAnimator().animatedX() - editor.getOffsetX();
        tmpRect.left = centerX - editor.getInsertSelectionWidth() / 2;
        tmpRect.right = centerX + editor.getInsertSelectionWidth() / 2;
        drawColor(canvas, editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT), tmpRect);
        if (editor.getEventHandler().shouldDrawInsertHandle() && !editor.isInMouseMode()) {
            editor.getHandleStyle().draw(canvas, SelectionHandleStyle.HANDLE_TYPE_INSERT, centerX, tmpRect.bottom, editor.getRowHeight(), editor.getColorScheme().getColor(EditorColorScheme.SELECTION_HANDLE), editor.getInsertHandleDescriptor());
        }
    }

    /**
     * Draw horizontal scroll bar
     *
     * @param canvas Canvas to draw
     */
    protected void drawScrollBarHorizontal(Canvas canvas) {
        int page = editor.getWidth();
        float all = editor.getScrollMaxX();
        float length = page / (all + editor.getWidth()) * editor.getWidth();
        float minLength = RenderingConstants.SCROLLBAR_LENGTH_MIN_DIP * editor.getDpUnit();
        if (length <= minLength) length = minLength;
        float leftX = editor.getOffsetX() / all * (editor.getWidth() - length);
        tmpRect.top = editor.getHeight() - editor.getDpUnit() * RenderingConstants.SCROLLBAR_WIDTH_DIP;
        tmpRect.bottom = editor.getHeight();
        tmpRect.right = leftX + length;
        tmpRect.left = leftX;
        horizontalScrollBarRect.set(tmpRect);
        if (horizontalScrollbarThumbDrawable != null) {
            horizontalScrollbarThumbDrawable.setState(editor.getEventHandler().holdHorizontalScrollBar() ? PRESSED_DRAWABLE_STATE : DEFAULT_DRAWABLE_STATE);
            horizontalScrollbarThumbDrawable.setBounds((int) tmpRect.left, (int) tmpRect.top, (int) tmpRect.right, (int) tmpRect.bottom);
            horizontalScrollbarThumbDrawable.draw(canvas);
        } else {
            drawColor(canvas, editor.getColorScheme().getColor(editor.getEventHandler().holdHorizontalScrollBar() ? EditorColorScheme.SCROLL_BAR_THUMB_PRESSED : EditorColorScheme.SCROLL_BAR_THUMB), tmpRect);
        }
    }

    // BEGIN Measure-------------------------------------

    @UnsupportedUserUsage
    public long findDesiredVisibleChar(float target, int lineIndex, int start, int end) {
        return findDesiredVisibleChar(target, lineIndex, start, end, start, false);
    }

    /**
     * Find first visible character
     *
     * @return packed floating number pair (textOffset, pixelOffset).
     */
    @UnsupportedUserUsage
    public long findDesiredVisibleChar(float target, int lineIndex, int start, int end, int contextStart, boolean forLast) {
        if (start >= end) {
            return CharPosDesc.make(end, 0);
        }
        var line = getLine(lineIndex);
        var context = editor.getRenderContext();
        var cache = context.getCache().queryMeasureCache(lineIndex);
        if (cache != null && cache.getWidths() != null && cache.getUpdateTimestamp() < displayTimestamp) {
            buildMeasureCacheForLines(lineIndex, lineIndex, displayTimestamp, false);
        }
        var gtr = GraphicTextRow.obtain(basicDisplayMode);
        gtr.set(content, lineIndex, contextStart, end, cache == null || cache.getWidths() == null ? editor.getSpansForLine(lineIndex) : null, paintGeneral, context);
        if (editor.getLayout() instanceof WordwrapLayout && (cache == null || cache.getWidths() == null)) {
            gtr.setSoftBreaks(((WordwrapLayout) editor.getLayout()).getSoftBreaksForLine(lineIndex));
        }
        var res = gtr.findOffsetByAdvance(start, target);

        // Do some additional work here

        var offset = CharPosDesc.getTextOffset(res);
        // Check RTL context
        var rtl = false;
        int runIndex = -1;
        Directions dirs = null;
        if (line.mayNeedBidi()) {
            dirs = content.getLineDirections(lineIndex);
            if (offset == line.length()) {
                runIndex = dirs.getRunCount() - 1;
            } else {
                for (int i = 0; i < dirs.getRunCount(); i++) {
                    if (offset >= dirs.getRunStart(i) && offset < dirs.getRunEnd(i)) {
                        runIndex = i;
                        rtl = dirs.isRunRtl(i);
                        break;
                    }
                }
            }
        }

        // Find actual desired position
        if (rtl) {
            if (forLast) {
                offset = dirs.getRunEnd(runIndex);
            } else {
                offset = dirs.getRunStart(runIndex);
            }
        } else {
            if (forLast) {
                if (offset + 1 < end) {
                    var itr = UnicodeIterator.obtain(line, offset + 1, end);
                    int codePoint;
                    var first = true;
                    while ((codePoint = itr.nextCodePoint()) != 0) {
                        if (isCombiningCharacter(codePoint) || first) {
                            offset = itr.getEndIndex();
                            first = false;
                        } else {
                            break;
                        }
                    }
                    itr.recycle();
                }
            } else {
                if (offset < end) {
                    var chars = line.getBackingCharArray();
                    while (offset > start) {
                        char ch = chars[offset];
                        if (Character.isLowSurrogate(ch)) {
                            if (offset - 1 >= start) {
                                if (isCombiningCharacter(Character.toCodePoint(chars[offset - 1], ch))) {
                                    offset -= 2;
                                } else if (isCombiningCharacter(ch)) {
                                    offset -= 1;
                                } else {
                                    break;
                                }
                            }
                        } else if (Character.isHighSurrogate(ch) || isCombiningCharacter(ch)) {
                            offset -= 1;
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        if (!rtl && !forLast && offset > start) {
            // Try to combine one character again
            var chars = line.getBackingCharArray();
            if (Character.isLowSurrogate(chars[offset - 1])) {
                if (offset - 1 > start && !couldBeEmojiPart(Character.toCodePoint(chars[offset - 2], chars[offset - 1]))) {
                    offset -= 2;
                }
            } else if (!Character.isHighSurrogate(chars[offset - 1])) {
                offset -= 1;
            }
        }
        offset = Math.min(end, Math.max(start, offset));
        long result = CharPosDesc.make(offset, gtr.measureText(start, offset));

        gtr.recycle();
        return result;
    }

    /**
     * Find first visible character
     *
     * @return Character position description, {@link CharPosDesc}
     */
    @UnsupportedUserUsage
    public long findFirstVisibleCharForWordwrap(float target, int lineIndex, int start, int end, int contextStart, Paint paint) {
        if (start >= end) {
            return CharPosDesc.make(end, 0);
        }
        var gtr = GraphicTextRow.obtain(basicDisplayMode);
        gtr.set(content, lineIndex, contextStart, end, sSpansForWordwrap, paint, editor.getRenderContext());
        gtr.disableCache();
        var res = gtr.findOffsetByAdvance(start, target);
        gtr.recycle();
        return res;
    }

    /**
     * Build measure cache for the given lines, if the timestamp indicates that it is outdated.
     */
    protected void buildMeasureCacheForLines(int startLine, int endLine, long timestamp, boolean useCachedContent) {
        var text = content;
        var context = editor.getRenderContext();
        while (startLine <= endLine && startLine < text.getLineCount()) {
            var line = useCachedContent ? getLine(startLine) : getLineDirect(startLine);
            var cache = editor.getRenderContext().getCache().getOrCreateMeasureCache(startLine);
            if (cache.getUpdateTimestamp() < timestamp) {
                var gtr = GraphicTextRow.obtain(basicDisplayMode);
                var forced = false;
                if (cache.getWidths() == null || cache.getWidths().length < line.length()) {
                    cache.setWidths(new float[Math.max(line.length() + 8, 90)]);
                    forced = true;
                }
                var spans = editor.getSpansForLine(startLine);
                gtr.set(text, startLine, 0, line.length(), spans, paintGeneral, context);
                var softBreaks = (editor.layout instanceof WordwrapLayout) ? ((WordwrapLayout) editor.layout).getSoftBreaksForLine(startLine) : null;
                gtr.setSoftBreaks(softBreaks);
                var hash = Objects.hash(spans, line.length(), editor.getTabWidth(), basicDisplayMode, softBreaks, paintGeneral.getFlags(), paintGeneral.getTextSize(), paintGeneral.getTextScaleX(), paintGeneral.getLetterSpacing(), paintGeneral.getFontFeatureSettings());
                if (context.getCache().getStyleHash(startLine) != hash || forced) {
                    gtr.buildMeasureCache();
                    context.getCache().setStyleHash(startLine, hash);
                }
                gtr.recycle();
                cache.setUpdateTimestamp(timestamp);
            }
            startLine++;
        }
    }

    protected void buildMeasureCacheForLines(int startLine, int endLine) {
        buildMeasureCacheForLines(startLine, endLine, displayTimestamp, false);
    }

    /**
     * Measure text width with editor's text paint
     *
     * @param text  Source string
     * @param index Start index in array
     * @param count Count of characters
     * @return The width measured
     */
    @UnsupportedUserUsage
    public float measureText(ContentLine text, int line, int index, int count) {
        var cache = editor.getRenderContext().getCache().queryMeasureCache(line);
        if (cache != null) {
            if (cache.getUpdateTimestamp() < displayTimestamp && cache.getWidths() != null || (cache.getWidths() != null && cache.getWidths().length >= index + count)) {
                buildMeasureCacheForLines(line, line);
            }
        }
        var gtr = GraphicTextRow.obtain(basicDisplayMode);
        List<Span> spans = editor.defaultSpans;
        if (cache == null || cache.getWidths() == null) {
            spans = editor.getSpansForLine(line);
        }
        gtr.set(content, line, 0, text.length(), spans, paintGeneral, editor.getRenderContext());
        if (editor.layout instanceof WordwrapLayout && (cache == null || cache.getWidths() == null)) {
            gtr.setSoftBreaks(((WordwrapLayout) editor.layout).getSoftBreaksForLine(line));
        }
        var res = gtr.measureText(index, index + count);
        gtr.recycle();
        return res;
    }

    // END Measure---------------------------------------

    protected interface PatchDraw {

        void draw(Canvas canvas, float horizontalOffset, int row, int line, int start, int end, long style);

    }

    protected static class TextDisplayPosition {

        protected int row, startColumn, endColumn, line, rowStart;
        protected float left;
        protected float right;

        @Override
        @NonNull
        public String toString() {
            return "TextDisplayPosition{" + "row=" + row + ", startColumn=" + startColumn + ", endColumn=" + endColumn + ", line=" + line + ", rowStart=" + rowStart + ", left=" + left + ", right=" + right + '}';
        }
    }

    protected class DrawCursorTask {

        protected float x;
        protected float y;
        protected int handleType;
        protected SelectionHandleStyle.HandleDescriptor descriptor;
        private final static SelectionHandleStyle.HandleDescriptor TMP_DESC = new SelectionHandleStyle.HandleDescriptor();

        public DrawCursorTask(float x, float y, int handleType, SelectionHandleStyle.HandleDescriptor descriptor) {
            this.x = x;
            this.y = y;
            this.handleType = handleType;
            this.descriptor = descriptor;
        }

        private boolean drawSelForLeftRight() {
            return ((handleType == SelectionHandleStyle.HANDLE_TYPE_LEFT || handleType == SelectionHandleStyle.HANDLE_TYPE_RIGHT)
                    && editor.getProps().showSelectionWhenSelected && !editor.isInMouseMode());
        }

        private boolean drawSelForInsert() {
            return (!(handleType == SelectionHandleStyle.HANDLE_TYPE_LEFT || handleType == SelectionHandleStyle.HANDLE_TYPE_RIGHT)
                    && (editor.getCursorBlink().visibility || editor.getEventHandler().holdInsertHandle() || editor.isInLongSelect()));
        }

        private boolean isSelForLongSelect() {
            return editor.isInLongSelect() && !(handleType == SelectionHandleStyle.HANDLE_TYPE_LEFT
                    || handleType == SelectionHandleStyle.HANDLE_TYPE_RIGHT);
        }

        protected void execute(Canvas canvas) {
            // Hide cursors (API level 31)
            if (handleType != SelectionHandleStyle.HANDLE_TYPE_UNDEFINED) {
                if (editor.inputConnection.imeConsumingInput || !editor.isFocused()) {
                    return;
                }
            }
            if (handleType == SelectionHandleStyle.HANDLE_TYPE_INSERT && !editor.isEditable()) {
                return;
            }
            var descriptor = this.descriptor == null ? TMP_DESC : this.descriptor;
            // Follow the thumb or stick to text row
            if (!descriptor.position.isEmpty()) {
                if (!editor.isStickyTextSelection()) {
                    if (editor.getEventHandler().getTouchedHandleType() == handleType
                            && handleType != SelectionHandleStyle.HANDLE_TYPE_UNDEFINED && editor.getEventHandler().isHandleMoving()) {
                        x = editor.getEventHandler().motionX + (descriptor.alignment != SelectionHandleStyle.ALIGN_CENTER ? descriptor.position.width() : 0) * (descriptor.alignment == SelectionHandleStyle.ALIGN_LEFT ? 1 : -1);
                        y = editor.getEventHandler().motionY - descriptor.position.height() * 2 / 3f;
                    }
                }
            }

            if (drawSelForLeftRight() || drawSelForInsert() || handleType == SelectionHandleStyle.HANDLE_TYPE_UNDEFINED) {
                float startY = y - (editor.getProps().textBackgroundWrapTextOnly ? editor.getRowHeightOfText() : editor.getRowHeight());
                float stopY = y;
                paintGeneral.setColor(editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT));
                paintGeneral.setStrokeWidth(editor.getInsertSelectionWidth());
                paintGeneral.setStyle(android.graphics.Paint.Style.STROKE);
                if (isSelForLongSelect()) {
                    paintGeneral.setPathEffect(new DashPathEffect(new float[]{(stopY - startY) / 8f, (stopY - startY) / 8f}, (stopY - startY) / 16f));
                    paintGeneral.setStrokeWidth(editor.getInsertSelectionWidth() * 1.5f);
                }
                canvas.drawLine(x, startY, x, stopY, paintGeneral);
                paintGeneral.setStyle(android.graphics.Paint.Style.FILL);
                paintGeneral.setPathEffect(null);
            }
            var handleType = this.handleType;
            // Hide insert handle conditionally
            if (handleType == SelectionHandleStyle.HANDLE_TYPE_INSERT && (editor.isInLongSelect() || !editor.getEventHandler().shouldDrawInsertHandle())) {
                handleType = SelectionHandleStyle.HANDLE_TYPE_UNDEFINED;
            }
            if (handleType != SelectionHandleStyle.HANDLE_TYPE_UNDEFINED && !editor.isInMouseMode() /* hide if mouse inside */) {
                editor.getHandleStyle().draw(canvas, handleType, x, y, editor.getRowHeight(), editor.getColorScheme().getColor(EditorColorScheme.SELECTION_HANDLE), descriptor);
                if (descriptor == TMP_DESC) {
                    descriptor.setEmpty();
                }
            } else {
                descriptor.setEmpty();
            }
        }
    }
}
