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

import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.annotations.UnsupportedUserUsage;
import io.github.rosemoe.sora.event.ClickEvent;
import io.github.rosemoe.sora.event.ContextClickEvent;
import io.github.rosemoe.sora.event.DoubleClickEvent;
import io.github.rosemoe.sora.event.EditorMotionEvent;
import io.github.rosemoe.sora.event.HandleStateChangeEvent;
import io.github.rosemoe.sora.event.InterceptTarget;
import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.SideIconClickEvent;
import io.github.rosemoe.sora.graphics.RectUtils;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.StylesUtils;
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.util.Numbers;
import io.github.rosemoe.sora.widget.component.Magnifier;
import io.github.rosemoe.sora.widget.style.SelectionHandleStyle;
import kotlin.jvm.functions.Function5;

/**
 * Handles touch events of editor
 *
 * @author Rosemoe
 */
@SuppressWarnings("CanBeFinal")
public final class EditorTouchEventHandler implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener {

    private final static int HIDE_DELAY = 3000;
    private final static int HIDE_DELAY_HANDLE = 3500;

    private final static int SCROLLBAR_FADE_ANIMATION_TIME = 200;
    private final static int MAGNIFIER_TOUCH_SLOP = 4;

    private final static int LEFT_EDGE = 1;
    private final static int RIGHT_EDGE = 1 << 1;
    private final static int TOP_EDGE = 1 << 2;
    private final static int BOTTOM_EDGE = 1 << 3;

    private final CodeEditor editor;
    private final EditorScroller scroller;
    private final SelectionHandle insertHandle;
    Magnifier magnifier;
    int selHandleType = -1;
    boolean selHandleMoving;
    float motionX;
    float motionY;
    boolean glowTopOrBottom; //true for bottom
    boolean glowLeftOrRight; //true for right
    public boolean isScaling = false;
    float scaleMaxSize;
    float scaleMinSize;
    private float textSizeStart;
    private long timeLastScroll = 0;
    private long timeLastSetSelection = 0;
    private boolean holdingScrollbarVertical = false;
    private boolean holdingScrollbarHorizontal = false;
    private float thumbDownY = 0;
    private float thumbDownX = 0;
    private SelectionHandle leftHandle;
    private SelectionHandle rightHandle;
    private float edgeFieldSize;
    private int edgeFlags;
    private final int touchSlop;
    private MotionEvent thumbMotionRecord;
    private float mouseDownX;
    private float mouseDownY;
    private int mouseDownButtonState;
    private long lastTimeMousePrimaryClickUp;
    private boolean mouseDoubleClick;
    private PointF lastContextClickPosition;
    boolean mouseClick;
    boolean mouseCanMoveText;
    CharPosition draggingSelection;

    /**
     * Create an event handler for the given editor
     *
     * @param editor Host editor
     */
    public EditorTouchEventHandler(@NonNull CodeEditor editor) {
        this.editor = editor;
        edgeFieldSize = editor.getDpUnit() * 18;
        scroller = new EditorScroller(editor);
        scaleMaxSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26, Resources.getSystem().getDisplayMetrics());
        scaleMinSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 8, Resources.getSystem().getDisplayMetrics());
        magnifier = new Magnifier(editor);
        leftHandle = new SelectionHandle(SelectionHandle.LEFT);
        rightHandle = new SelectionHandle(SelectionHandle.RIGHT);
        insertHandle = new SelectionHandle(SelectionHandle.BOTH);
        var config = ViewConfiguration.get(editor.getContext());
        touchSlop = config.getScaledTouchSlop();
    }

    public boolean hasAnyHeldHandle() {
        return selHandleType != -1;
    }

    public boolean isHandleMoving() {
        return selHandleMoving;
    }

    /**
     * Whether we should draw scroll bars
     *
     * @return whether draw scroll bars
     */
    public boolean shouldDrawScrollBarForTouch() {
        return System.currentTimeMillis() - timeLastScroll < HIDE_DELAY + SCROLLBAR_FADE_ANIMATION_TIME || holdingScrollbarVertical || holdingScrollbarHorizontal;
    }

    @UnsupportedUserUsage
    public float getScrollBarFadeOutPercentageForTouch() {
        if (System.currentTimeMillis() - timeLastScroll < HIDE_DELAY || holdingScrollbarVertical || holdingScrollbarHorizontal) {
            return 0f;
        } else if (System.currentTimeMillis() - timeLastScroll >= HIDE_DELAY && System.currentTimeMillis() - timeLastScroll < HIDE_DELAY + SCROLLBAR_FADE_ANIMATION_TIME) {
            editor.postInvalidateOnAnimation();
            return (System.currentTimeMillis() - timeLastScroll - HIDE_DELAY) * 1f / SCROLLBAR_FADE_ANIMATION_TIME;
        }
        return 1f;
    }

    /**
     * Hide the insert handle at once
     */
    public void hideInsertHandle() {
        if (!shouldDrawInsertHandle()) {
            return;
        }
        timeLastSetSelection = 0;
        editor.invalidate();
    }

    /**
     * Whether the vertical scroll bar is touched
     *
     * @return Whether touched
     */
    public boolean holdVerticalScrollBar() {
        return holdingScrollbarVertical;
    }

    /**
     * Whether the horizontal scroll bar is touched
     *
     * @return Whether touched
     */
    public boolean holdHorizontalScrollBar() {
        return holdingScrollbarHorizontal;
    }

    /**
     * Whether insert handle is touched
     *
     * @return Whether touched
     */
    public boolean holdInsertHandle() {
        return selHandleType == SelectionHandle.BOTH;
    }

    /**
     * Whether the editor should draw insert handler
     *
     * @return Whether to draw
     */
    public boolean shouldDrawInsertHandle() {
        return (System.currentTimeMillis() - timeLastSetSelection < HIDE_DELAY_HANDLE || holdInsertHandle());
    }

    /**
     * Notify the editor later to hide scroll bars
     */
    public void notifyScrolled() {
        timeLastScroll = System.currentTimeMillis();
        class ScrollNotifier implements Runnable {

            @Override
            public void run() {
                if (System.currentTimeMillis() - timeLastScroll >= HIDE_DELAY) {
                    editor.invalidate();
                }
            }

        }
        editor.postDelayedInLifecycle(new ScrollNotifier(), HIDE_DELAY);
    }

    /**
     * Notify the editor later to hide insert handle
     */
    public void notifyLater() {
        timeLastSetSelection = System.currentTimeMillis();
        class InvalidateNotifier implements Runnable {

            @Override
            public void run() {
                if (System.currentTimeMillis() - timeLastSetSelection >= HIDE_DELAY_HANDLE) {
                    editor.invalidate();
                }
            }

        }
        editor.postDelayedInLifecycle(new InvalidateNotifier(), HIDE_DELAY_HANDLE);
    }

    /**
     * Called by editor
     * Whether this class is handling motions by user
     *
     * @return Whether handling
     */
    public boolean handlingMotions() {
        return holdHorizontalScrollBar() || holdVerticalScrollBar() || hasAnyHeldHandle();
    }

    /**
     * Get scroller for editor
     *
     * @return Scroller using
     */
    public EditorScroller getScroller() {
        return scroller;
    }

    /**
     * Reset states of handler
     */
    public void reset() {
        scroller.startScroll(0, 0, 0, 0, 0);
        reset2();
    }

    /**
     * Reset states of handler, except scrolling state
     */
    public void reset2() {
        holdingScrollbarHorizontal = holdingScrollbarVertical = false;
        selHandleType = -1;
        dismissMagnifier();
    }

    private SelectionHandleStyle.HandleDescriptor getHandleDescriptorByType(int type) {
        return switch (type) {
            case SelectionHandle.BOTH -> editor.getInsertHandleDescriptor();
            case SelectionHandle.LEFT -> editor.getLeftHandleDescriptor();
            case SelectionHandle.RIGHT -> editor.getRightHandleDescriptor();
            default -> null;
        };
    }

    public void updateMagnifier(MotionEvent e) {
        if (edgeFlags != 0 || !hasAnyHeldHandle() || !magnifier.isEnabled()) {
            dismissMagnifier();
            return;
        }
        // A handle is already held
        var pos = getHandleDescriptorByType(selHandleType).position;

        var height = pos.height();
        int x, y;
        if (editor.isStickyTextSelection()) {
            x = Math.min((int) e.getX(), (int) pos.right);
            y = (int) (pos.top - height / 2);
        } else {
            x = (int) e.getX();
            y = (int) (e.getY() - height / 2 - editor.getRowHeight());
        }
        magnifier.show(x, y);
    }

    public void dismissMagnifier() {
        magnifier.dismiss();
    }

    /**
     * Handle events apart from detectors
     *
     * @param e The event editor received
     * @return Whether this touch event is handled by this class
     */
    public boolean onTouchEvent(MotionEvent e) {
        motionY = e.getY();
        motionX = e.getX();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                thumbDownY = e.getY();
                thumbDownX = e.getX();
                holdingScrollbarVertical = holdingScrollbarHorizontal = false;
                RectF rect = editor.getRenderer().getVerticalScrollBarRect();
                if (RectUtils.contains(rect, e.getX(), e.getY(), editor.getDpUnit() * 10)) {
                    holdingScrollbarVertical = true;
                }
                rect = editor.getRenderer().getHorizontalScrollBarRect();
                if (rect.contains(e.getX(), e.getY())) {
                    holdingScrollbarHorizontal = true;
                }
                if (holdingScrollbarVertical || holdingScrollbarHorizontal) {
                    if (holdingScrollbarVertical && holdingScrollbarHorizontal) {
                        holdingScrollbarHorizontal = false;
                    }
                    editor.invalidate();
                } else {
                    final var allowedDistance = editor.getDpUnit() * 7;
                    if (shouldDrawInsertHandle() && RectUtils.almostContains(editor.getInsertHandleDescriptor().position, e.getX(), e.getY(), allowedDistance)) {
                        selHandleType = SelectionHandle.BOTH;
                    }
                    boolean left = RectUtils.almostContains(editor.getLeftHandleDescriptor().position, e.getX(), e.getY(), allowedDistance);
                    boolean right = RectUtils.almostContains(editor.getRightHandleDescriptor().position, e.getX(), e.getY(), allowedDistance);
                    if (left) {
                        selHandleType = SelectionHandle.LEFT;
                    } else if (right) {
                        selHandleType = SelectionHandle.RIGHT;
                    }
                    if (selHandleType != -1) {
                        selHandleMoving = false;
                        dispatchHandleStateChange(selHandleType, true);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (holdingScrollbarVertical) {
                    float movedDis = e.getY() - thumbDownY;
                    thumbDownY = e.getY();
                    float all = editor.getScrollMaxY();
                    float dy = movedDis / (editor.getHeight() - editor.getRenderer().getVerticalScrollBarRect().height()) * all;
                    scrollBy(0, dy);
                    return true;
                }
                if (holdingScrollbarHorizontal) {
                    float movedDis = e.getX() - thumbDownX;
                    thumbDownX = e.getX();
                    float all = editor.getScrollMaxX() + editor.getWidth();
                    float dx;
                    if (editor.getRenderer().getHorizontalScrollBarRect().width() <= 60 * editor.getDpUnit()) {
                        dx = movedDis / (editor.getWidth() - editor.getRenderer().getHorizontalScrollBarRect().width()) * all;
                    } else {
                        dx = movedDis / editor.getWidth() * all;
                    }
                    scrollBy(dx, 0);
                    return true;
                }
                if (!selHandleMoving && (Math.abs(e.getX() - thumbDownX) > touchSlop || Math.abs(e.getY() - thumbDownY) > touchSlop)) {
                    selHandleMoving = true;
                }
                if (selHandleMoving && handleSelectionChange(e)) {
                    if (magnifier.isShowing() || Math.sqrt((e.getX() - thumbDownX) * (e.getX() - thumbDownX) +
                            (e.getY() - thumbDownY) * (e.getY() - thumbDownY)) >= MAGNIFIER_TOUCH_SLOP) {
                        updateMagnifier(e);
                    }
                    editor.invalidate();
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (holdingScrollbarVertical || holdingScrollbarHorizontal) {
                    holdingScrollbarVertical = holdingScrollbarHorizontal = false;
                    timeLastScroll = System.currentTimeMillis();
                    notifyScrolled();
                }
                if (selHandleType != -1) {
                    dispatchHandleStateChange(selHandleType, false);
                    if (selHandleType == SelectionHandle.BOTH)
                        notifyLater();
                    selHandleType = -1;
                }
                editor.invalidate();
                stopEdgeScroll();
                dismissMagnifier();
                break;
        }
        return false;
    }

    private boolean shouldForwardToTouch() {
        return holdingScrollbarHorizontal || holdingScrollbarVertical;
    }

    /**
     * Entry for mouse motion events
     */
    public boolean onMouseEvent(MotionEvent event) {
        if (editor.isFormatting()) {
            resetMouse();
            return false;
        }
        if (shouldForwardToTouch()) {
            return onTouchEvent(event);
        }
        lastContextClickPosition = null;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                mouseDownX = event.getX();
                mouseDownY = event.getY();
                mouseDownButtonState = event.getButtonState();
                mouseClick = true;
                if ((mouseDownButtonState & MotionEvent.BUTTON_PRIMARY) != 0) {
                    if (onTouchEvent(event) && shouldForwardToTouch()) {
                        return true;
                    }
                    if (SystemClock.uptimeMillis() - lastTimeMousePrimaryClickUp < ViewConfiguration.getDoubleTapTimeout()) {
                        mouseDoubleClick = true;
                        onDoubleTap(event);
                        return true;
                    }
                    var pos = editor.getPointPositionOnScreen(mouseDownX, mouseDownY);
                    int line = IntPair.getFirst(pos), column = IntPair.getSecond(pos);
                    var charPos = editor.getText().getIndexer().getCharPosition(line, column);
                    if (editor.isTextSelected() && editor.getCursorRange().isPositionInside(charPos) && editor.isScreenPointOnText(mouseDownX, mouseDownY)) {
                        mouseCanMoveText = true;
                    } else {
                        mouseCanMoveText = false;
                        editor.setSelection(line, column, SelectionChangeEvent.CAUSE_MOUSE_INPUT);
                        editor.requestFocus();
                    }
                    draggingSelection = charPos;
                    editor.postInvalidate();
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                if (mouseDoubleClick) {
                    return true;
                }
                if (Math.abs(event.getX() - mouseDownX) > touchSlop || Math.abs(event.getY() - mouseDownY) > touchSlop) {
                    mouseClick = false;
                }
                if ((mouseDownButtonState & MotionEvent.BUTTON_PRIMARY) != 0) {
                    var pos = editor.getPointPositionOnScreen(event.getX(), event.getY());
                    int line = IntPair.getFirst(pos), column = IntPair.getSecond(pos);
                    var charPos = editor.getText().getIndexer().getCharPosition(line, column);
                    if (!mouseClick && !mouseCanMoveText) {
                        var anchor = editor.selectionAnchor;
                        editor.setSelectionRegion(anchor.line, anchor.column, line, column, SelectionChangeEvent.CAUSE_MOUSE_INPUT);
                    }
                    draggingSelection = charPos;
                    editor.postInvalidate();
                    scrollIfThumbReachesEdge(event);
                }
            }
            case MotionEvent.ACTION_UP -> {
                if (event.getEventTime() - event.getDownTime() > ViewConfiguration.getTapTimeout() * 2f) {
                    mouseClick = false;
                }
                if (!mouseDoubleClick) {
                    if (mouseCanMoveText && !mouseClick && (mouseDownButtonState & MotionEvent.BUTTON_PRIMARY) != 0) {
                        var pos = editor.getPointPositionOnScreen(event.getX(), event.getY());
                        int line = IntPair.getFirst(pos), column = IntPair.getSecond(pos);
                        var dest = editor.getText().getIndexer().getCharPosition(line, column);
                        var curRange = editor.getCursorRange();
                        if (!curRange.isPositionInside(dest) && (editor.getKeyMetaStates().isCtrlPressed() || !curRange.getEnd().equals(dest))) {
                            int length = (curRange.getEndIndex() - curRange.getStartIndex());
                            int insIndex = editor.getKeyMetaStates().isCtrlPressed() ? dest.index : dest.index < curRange.getStartIndex() ? dest.index : dest.index - length;
                            var text = editor.getText();
                            var insText = text.substring(curRange.getStartIndex(), curRange.getEndIndex());
                            CharPosition insPos;
                            if (editor.getKeyMetaStates().isCtrlPressed()) {
                                text.insert(dest.line, dest.column, insText);
                                insPos = dest;
                            } else {
                                text.beginBatchEdit();
                                editor.deleteText();
                                insPos = text.getIndexer().getCharPosition(insIndex);
                                text.insert(insPos.line, insPos.column, insText);
                                text.endBatchEdit();
                            }
                            var endPos = text.getIndexer().getCharPosition(insIndex + length);
                            editor.setSelectionRegion(insPos.getLine(), insPos.getColumn(), endPos.getLine(), endPos.getColumn(), SelectionChangeEvent.CAUSE_MOUSE_INPUT);
                        }
                    }
                    if (mouseClick) {
                        if ((mouseDownButtonState & MotionEvent.BUTTON_PRIMARY) != 0) {
                            onSingleTapUp(event);
                            lastTimeMousePrimaryClickUp = event.getEventTime();
                        } else if ((mouseDownButtonState & MotionEvent.BUTTON_SECONDARY) != 0) {
                            onContextClick(event);
                        }
                    }
                }
                resetMouse();
                stopEdgeScroll();
            }
            case MotionEvent.ACTION_CANCEL -> {
                resetMouse();
                stopEdgeScroll();
            }
        }
        return true;
    }

    /**
     * Reset mouse handling state
     */
    public void resetMouse() {
        mouseDownX = mouseDownY = 0f;
        mouseClick = false;
        mouseCanMoveText = false;
        draggingSelection = null;
        if (mouseDoubleClick) {
            mouseDoubleClick = false;
            lastTimeMousePrimaryClickUp = 0L;
        }
    }

    /**
     * Context click
     */
    public void onContextClick(MotionEvent event) {
        lastContextClickPosition = new PointF(event.getX(), event.getY());
        if ((dispatchEditorMotionEvent(ContextClickEvent::new, null, event) & InterceptTarget.TARGET_EDITOR) != 0) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            editor.performContextClick(event.getX(), event.getY());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            editor.performContextClick();
        }

        if (editor.getProps().mouseContextMenu) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                editor.showContextMenu(event.getX(), event.getY());
            } else {
                editor.showContextMenu();
            }
        }
    }

    @Nullable
    public PointF getLastContextClickPosition() {
        return lastContextClickPosition;
    }

    private void dispatchHandleStateChange(int type, boolean held) {
        editor.dispatchEvent(new HandleStateChangeEvent(editor, type, held));
    }

    int dispatchEditorMotionEvent
            (Function5<CodeEditor, CharPosition, MotionEvent, Span, TextRange, EditorMotionEvent> constructor,
             @Nullable CharPosition pos, @NonNull MotionEvent event) {
        if (pos == null) {
            var pt = editor.getPointPositionOnScreen(event.getX(), event.getY());
            pos = editor.getText().getIndexer().getCharPosition(IntPair.getFirst(pt), IntPair.getSecond(pt));
        }
        var styles = editor.getStyles();
        var text = editor.getText();
        var span = StylesUtils.getSpanForPosition(styles, pos);
        var nextSpan = StylesUtils.getFollowingSpanForPosition(styles, pos);
        TextRange range = null;
        if (span != null) {
            var startPos = text.getIndexer().getCharPosition(pos.line, Numbers.coerceIn(span.getColumn(), 0, text.getColumnCount(pos.line)));
            var endPos = nextSpan != null ?
                    text.getIndexer().getCharPosition(pos.line, Numbers.coerceIn(nextSpan.getColumn(), 0, text.getColumnCount(pos.line)))
                    : text.getIndexer().getCharPosition(pos.line, text.getColumnCount(pos.line));
            range = new TextRange(startPos, endPos);
        }
        return editor.dispatchEvent(constructor.invoke(editor, pos, event, span, range));
    }

    private boolean handleSelectionChange(MotionEvent e) {
        switch (selHandleType) {
            case SelectionHandle.BOTH:
                insertHandle.applyPosition(e);
                scrollIfThumbReachesEdge(e);
                return true;
            case SelectionHandle.LEFT:
                editor.selectionAnchor = editor.getCursor().right();
                leftHandle.applyPosition(e);
                scrollIfThumbReachesEdge(e);
                return true;
            case SelectionHandle.RIGHT:
                editor.selectionAnchor = editor.getCursor().left();
                rightHandle.applyPosition(e);
                scrollIfThumbReachesEdge(e);
                return true;
        }
        return false;
    }

    private void handleSelectionChange2(MotionEvent e) {
        switch (selHandleType) {
            case SelectionHandle.BOTH:
                insertHandle.applyPosition(e);
                break;
            case SelectionHandle.LEFT:
                leftHandle.applyPosition(e);
                break;
            case SelectionHandle.RIGHT:
                rightHandle.applyPosition(e);
                break;
        }
    }

    private int computeEdgeFlags(float x, float y) {
        int flags = 0;
        if (x < edgeFieldSize) {
            flags |= LEFT_EDGE;
        }
        if (y < edgeFieldSize) {
            flags |= TOP_EDGE;
        }
        if (x > editor.getWidth() - edgeFieldSize) {
            flags |= RIGHT_EDGE;
        }
        if (y > editor.getHeight() - edgeFieldSize) {
            flags |= BOTTOM_EDGE;
        }
        return flags;
    }

    public void scrollIfThumbReachesEdge(@Nullable MotionEvent e) {
        scrollIfReachesEdge(e, 0, 0);
    }

    public void scrollIfReachesEdge(@Nullable MotionEvent e, float x, float y) {
        if (e != null) {
            x = e.getX();
            y = e.getY();
        }
        int flag = computeEdgeFlags(x, y);
        if (flag != 0) {
            var oldFlags = edgeFlags;
            edgeFlags = flag;
            thumbMotionRecord = e == null ? null : MotionEvent.obtain(e);
            if (oldFlags == 0) {
                int initialDelta = (int) (8 * editor.getDpUnit());
                editor.postInLifecycle(new EdgeScrollRunnable(initialDelta));
            }
        } else {
            stopEdgeScroll();
        }
    }

    private boolean isSameSign(float a, float b) {
        if (Math.abs(a) < 1e5 || Math.abs(b) < 1e5) {
            return false;
        }
        return (a < 0 && b < 0) || (a > 0 && b > 0);
    }

    public void stopEdgeScroll() {
        edgeFlags = 0;
    }

    public void scrollBy(float distanceX, float distanceY) {
        scrollBy(distanceX, distanceY, false);
    }

    public void scrollBy(float distanceX, float distanceY, boolean smooth) {
        int endX = scroller.getCurrX() + (int) distanceX;
        int endY = scroller.getCurrY() + (int) distanceY;
        endX = Math.max(endX, 0);
        endY = Math.max(endY, 0);
        endY = Math.min(endY, editor.getScrollMaxY());
        endX = Math.min(endX, editor.getScrollMaxX());
        editor.dispatchEvent(new ScrollEvent(editor, scroller.getCurrX(),
                scroller.getCurrY(), endX, endY, ScrollEvent.CAUSE_USER_DRAG));
        if (smooth) {
            scroller.startScroll(scroller.getCurrX(),
                    scroller.getCurrY(),
                    endX - scroller.getCurrX(),
                    endY - scroller.getCurrY());
        } else {
            scroller.startScroll(scroller.getCurrX(),
                    scroller.getCurrY(),
                    endX - scroller.getCurrX(),
                    endY - scroller.getCurrY(), 0);
            scroller.abortAnimation();
        }
        editor.invalidate();
    }

    public int getTouchedHandleType() {
        return selHandleType;
    }

    @Override
    public boolean onSingleTapUp(@NonNull MotionEvent e) {
        scroller.forceFinished(true);
        if (editor.isFormatting()) {
            return true;
        }
        var resolved = RegionResolverKt.resolveTouchRegion(editor, e);
        var region = IntPair.getFirst(resolved);
        long res = editor.getPointPositionOnScreen(e.getX(), e.getY());
        int line = IntPair.getFirst(res);
        int column = IntPair.getSecond(res);
        editor.performClick();
        if (region == RegionResolverKt.REGION_SIDE_ICON) {
            int row = (int) (e.getY() + editor.getOffsetX()) / editor.getRowHeight();
            row = Math.max(0, Math.min(row, editor.getLayout().getRowCount() - 1));
            var inf = editor.getLayout().getRowAt(row);
            if (inf.isLeadingRow) {
                var style = editor.getRenderer().getLineStyle(inf.lineIndex, LineSideIcon.class);
                if (style != null) {
                    if ((editor.dispatchEvent(new SideIconClickEvent(editor, style)) & InterceptTarget.TARGET_EDITOR) != 0) {
                        return true;
                    }
                }
            }
        }
        var position = editor.getText().getIndexer().getCharPosition(line, column);
        if ((dispatchEditorMotionEvent(ClickEvent::new, position, e) & InterceptTarget.TARGET_EDITOR) != 0) {
            return true;
        }
        editor.showSoftInput();
        notifyLater();
        var lnAction = editor.getProps().actionWhenLineNumberClicked;
        if (region == RegionResolverKt.REGION_TEXT) {
            if (editor.isInLongSelect()) {
                var cursor = editor.getCursor();
                editor.setSelectionRegion(cursor.getLeftLine(), cursor.getLeftColumn(), line, column, false, SelectionChangeEvent.CAUSE_TAP);
                editor.endLongSelect();
            } else {
                editor.setSelection(line, column, SelectionChangeEvent.CAUSE_TAP);
            }
        } else if (region == RegionResolverKt.REGION_LINE_NUMBER) {
            switch (lnAction) {
                case DirectAccessProps.LN_ACTION_SELECT_LINE:
                    editor.setSelectionRegion(line, 0, line, editor.getText().getColumnCount(line), false, SelectionChangeEvent.CAUSE_TAP);
                    break;
                case DirectAccessProps.LN_ACTION_PLACE_SELECTION_HOME:
                    editor.setSelection(line, column, SelectionChangeEvent.CAUSE_TAP);
                    break;
                case DirectAccessProps.LN_ACTION_NOTHING:
                default:
                    // do nothing
            }
        }
        return true;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent e) {
        if (editor.isFormatting()) {
            return;
        }
        long res = editor.getPointPositionOnScreen(e.getX(), e.getY());
        int line = IntPair.getFirst(res);
        int column = IntPair.getSecond(res);
        if ((dispatchEditorMotionEvent(LongPressEvent::new, editor.getText().getIndexer().getCharPosition(line, column), e) & InterceptTarget.TARGET_EDITOR) != 0) {
            return;
        }
        if ((!editor.getProps().reselectOnLongPress && editor.getCursor().isSelected()) || e.getPointerCount() != 1) {
            return;
        }
        editor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        editor.selectWord(line, column);
    }

    @Override
    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        if (editor.getProps().singleDirectionDragging) {
            if (Math.abs(distanceX) > Math.abs(distanceY)) {
                distanceY = 0;
            } else {
                distanceX = 0;
            }
        }
        int endX = scroller.getCurrX() + (int) distanceX;
        int endY = scroller.getCurrY() + (int) distanceY;
        endX = Math.max(endX, 0);
        endY = Math.max(endY, 0);
        endY = Math.min(endY, editor.getScrollMaxY());
        endX = Math.min(endX, editor.getScrollMaxX());
        boolean notifyY = true;
        boolean notifyX = true;
        if (!editor.getVerticalEdgeEffect().isFinished()) {
            float displacement = Math.max(0, Math.min(1, e2.getX() / editor.getWidth()));
            float distance = (glowTopOrBottom ? distanceY : -distanceY) / editor.getMeasuredHeight();
            if (distance > 0) {
                endY = scroller.getCurrY();
                editor.getVerticalEdgeEffect().onPull(distance, !glowTopOrBottom ? displacement : 1 - displacement);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                var edgeEffect = editor.getVerticalEdgeEffect();
                edgeEffect.onPullDistance(distance, !glowTopOrBottom ? displacement : 1 - displacement);
                if (edgeEffect.getDistance() != 0) {
                    endY = scroller.getCurrY();
                }
            } else {
                editor.getVerticalEdgeEffect().finish();
            }
            notifyY = false;
        }
        if (!editor.getHorizontalEdgeEffect().isFinished()) {
            float displacement = Math.max(0, Math.min(1, e2.getY() / editor.getHeight()));
            float distance = (glowLeftOrRight ? distanceX : -distanceX) / editor.getMeasuredWidth();
            if (distance > 0) {
                endX = scroller.getCurrX();
                editor.getHorizontalEdgeEffect().onPull(distance, !glowLeftOrRight ? 1 - displacement : displacement);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                var edgeEffect = editor.getHorizontalEdgeEffect();
                edgeEffect.onPullDistance(distance, !glowLeftOrRight ? 1 - displacement : displacement);
                if (edgeEffect.getDistance() != 0) {
                    endX = scroller.getCurrX();
                }
            } else {
                editor.getHorizontalEdgeEffect().finish();
            }
            notifyX = false;
        }
        scroller.startScroll(scroller.getCurrX(),
                scroller.getCurrY(),
                endX - scroller.getCurrX(),
                endY - scroller.getCurrY(), 0);
        final float minOverPull = 2f;
        if (notifyY && scroller.getCurrY() + distanceY < -minOverPull) {
            editor.getVerticalEdgeEffect().onPull(-distanceY / editor.getMeasuredHeight(), Math.max(0, Math.min(1, e2.getX() / editor.getWidth())));
            glowTopOrBottom = false;
        }
        if (notifyY && scroller.getCurrY() + distanceY > editor.getScrollMaxY() + minOverPull) {
            editor.getVerticalEdgeEffect().onPull(distanceY / editor.getMeasuredHeight(), Math.max(0, Math.min(1, e2.getX() / editor.getWidth())));
            glowTopOrBottom = true;
        }
        if (notifyX && scroller.getCurrX() + distanceX < -minOverPull) {
            editor.getHorizontalEdgeEffect().onPull(-distanceX / editor.getMeasuredWidth(), Math.max(0, Math.min(1, e2.getY() / editor.getHeight())));
            glowLeftOrRight = false;
        }
        if (notifyX && scroller.getCurrX() + distanceX > editor.getScrollMaxX() + minOverPull) {
            editor.getHorizontalEdgeEffect().onPull(distanceX / editor.getMeasuredWidth(), Math.max(0, Math.min(1, e2.getY() / editor.getHeight())));
            glowLeftOrRight = true;
        }
        editor.invalidate();
        editor.dispatchEvent(new ScrollEvent(editor, scroller.getCurrX(),
                scroller.getCurrY(), endX, endY, ScrollEvent.CAUSE_USER_DRAG));
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
        if (editor.getProps().singleDirectionFling) {
            if (Math.abs(velocityX) > Math.abs(velocityY)) {
                velocityY = 0;
            } else {
                velocityX = 0;
            }
        }
        if (!editor.getProps().scrollFling) {
            return false;
        }
        // If we do not finish it here, it can produce a high speed and cause the final scroll range to be broken, even a NaN for velocity
        scroller.forceFinished(true);
        scroller.fling(scroller.getCurrX(),
                scroller.getCurrY(),
                (int) -velocityX,
                (int) -velocityY,
                0,
                editor.getScrollMaxX(),
                0,
                editor.getScrollMaxY(),
                editor.getProps().overScrollEnabled && !editor.isWordwrap() ? (int) (20 * editor.getDpUnit()) : 0,
                editor.getProps().overScrollEnabled ? (int) (20 * editor.getDpUnit()) : 0);
        float minVe = editor.getDpUnit() * 2000;
        if (Math.abs(velocityX) >= minVe || Math.abs(velocityY) >= minVe) {
            notifyScrolled();
        }
        editor.releaseEdgeEffects();
        editor.dispatchEvent(new ScrollEvent(editor, scroller.getCurrX(),
                scroller.getCurrY(), scroller.getFinalX(), scroller.getFinalY(), ScrollEvent.CAUSE_USER_FLING));
        editor.postInvalidateOnAnimation();
        return false;
    }

    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        if (editor.isFormatting()) {
            return true;
        }
        if (editor.isScalable()) {
            float newSize = editor.getTextSizePx() * detector.getScaleFactor();
            if (newSize < scaleMinSize || newSize > scaleMaxSize) {
                return true;
            }
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            int originHeight = editor.getRowHeight();
            editor.setTextSizePxDirect(newSize);
            float heightFactor = editor.getRowHeight() * 1f / originHeight;
            float afterScrollY = (scroller.getCurrY() + focusY) * heightFactor - focusY;
            float afterScrollX = (scroller.getCurrX() + focusX) * detector.getScaleFactor() - focusX;
            afterScrollX = Math.max(0, Math.min(afterScrollX, editor.getScrollMaxX()));
            afterScrollY = Math.max(0, Math.min(afterScrollY, editor.getScrollMaxY()));
            editor.dispatchEvent(new ScrollEvent(editor, scroller.getCurrX(),
                    scroller.getCurrY(), (int) afterScrollX, (int) afterScrollY, ScrollEvent.CAUSE_SCALE_TEXT));
            scroller.startScroll((int) afterScrollX, (int) afterScrollY, 0, 0, 0);
            scroller.abortAnimation();
            isScaling = true;
            editor.invalidate();
            return true;
        }
        return false;
    }

    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        scroller.forceFinished(true);
        textSizeStart = editor.getTextSizePx();
        return editor.isScalable() && !editor.isFormatting() && !hasAnyHeldHandle();
    }

    long memoryPosition;
    boolean positionNotApplied;
    float focusY;

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
        isScaling = false;
        if (textSizeStart == editor.getTextSizePx()) {
            return;
        }
        editor.getRenderer().forcedRecreateLayout = true;
        if (editor.isWordwrap()) {
            focusY = detector.getFocusY();
            memoryPosition = editor.getPointPositionOnScreen(detector.getFocusX(), detector.getFocusY());
            positionNotApplied = true;
        } else {
            positionNotApplied = false;
        }
        editor.getRenderContext().invalidateRenderNodes();
        editor.getRenderer().updateTimestamp();
        editor.invalidate();
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        return editor.isEnabled();
    }

    @Override
    public void onShowPress(@NonNull MotionEvent e) {
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        return true;
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (editor.isFormatting()) {
            return true;
        }
        long res = editor.getPointPositionOnScreen(e.getX(), e.getY());
        int line = IntPair.getFirst(res);
        int column = IntPair.getSecond(res);
        if ((dispatchEditorMotionEvent(DoubleClickEvent::new, editor.getText().getIndexer().getCharPosition(line, column), e) & InterceptTarget.TARGET_EDITOR) != 0) {
            return true;
        }
        if (editor.getCursor().isSelected() || e.getPointerCount() != 1) {
            return true;
        }
        editor.selectWord(line, column);
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
        return true;
    }


    /**
     * This is a helper for EventHandler to control handles
     */
    final class SelectionHandle {

        public static final int LEFT = HandleStateChangeEvent.HANDLE_TYPE_LEFT;
        public static final int RIGHT = HandleStateChangeEvent.HANDLE_TYPE_RIGHT;
        public static final int BOTH = HandleStateChangeEvent.HANDLE_TYPE_INSERT;

        public int type;

        /**
         * Create a handle
         *
         * @param type Type :left,right,both
         */
        public SelectionHandle(int type) {
            this.type = type;
        }

        private boolean checkNoIntersection(SelectionHandleStyle.HandleDescriptor one, SelectionHandleStyle.HandleDescriptor another) {
            return !RectF.intersects(one.position, another.position);
        }

        /**
         * Handle the event
         *
         * @param e Event sent by EventHandler
         */
        public void applyPosition(MotionEvent e) {
            SelectionHandleStyle.HandleDescriptor descriptor = switch (type) {
                case LEFT -> editor.getLeftHandleDescriptor();
                case RIGHT -> editor.getRightHandleDescriptor();
                default -> editor.getInsertHandleDescriptor();
            };
            var anotherDesc = type == LEFT ? editor.getRightHandleDescriptor() : editor.getLeftHandleDescriptor();
            float targetX = scroller.getCurrX() + e.getX() + (descriptor.alignment != SelectionHandleStyle.ALIGN_CENTER ? descriptor.position.width() : 0) * (descriptor.alignment == SelectionHandleStyle.ALIGN_LEFT ? 1 : -1);
            float targetY = scroller.getCurrY() + e.getY() - descriptor.position.height();
            int line = IntPair.getFirst(editor.getPointPosition(0, targetY));
            if (line >= 0 && line < editor.getLineCount()) {
                int column = IntPair.getSecond(editor.getPointPosition(targetX, targetY));
                int lastLine = type == RIGHT ? editor.getCursor().getRightLine() : editor.getCursor().getLeftLine();
                int lastColumn = type == RIGHT ? editor.getCursor().getRightColumn() : editor.getCursor().getLeftColumn();
                int anotherLine = type != RIGHT ? editor.getCursor().getRightLine() : editor.getCursor().getLeftLine();
                int anotherColumn = type != RIGHT ? editor.getCursor().getRightColumn() : editor.getCursor().getLeftColumn();

                if ((line != lastLine || column != lastColumn) && (type == BOTH || (line != anotherLine || column != anotherColumn))) {
                    switch (type) {
                        case BOTH:
                            editor.cancelAnimation();
                            editor.setSelection(line, column, false, SelectionChangeEvent.CAUSE_SELECTION_HANDLE);
                            break;
                        case RIGHT:
                            if (anotherLine > line || (anotherLine == line && anotherColumn > column)) {
                                //Swap type
                                if (checkNoIntersection(descriptor, anotherDesc)) {
                                    dispatchHandleStateChange(selHandleType, false);
                                    EditorTouchEventHandler.this.selHandleType = LEFT;
                                    dispatchHandleStateChange(selHandleType, true);
                                    this.type = LEFT;
                                    leftHandle.type = RIGHT;
                                    SelectionHandle tmp = rightHandle;
                                    rightHandle = leftHandle;
                                    leftHandle = tmp;
                                    editor.setSelectionRegion(line, column, anotherLine, anotherColumn, false, SelectionChangeEvent.CAUSE_SELECTION_HANDLE);
                                }
                            } else {
                                editor.setSelectionRegion(anotherLine, anotherColumn, line, column, false, SelectionChangeEvent.CAUSE_SELECTION_HANDLE);
                            }
                            break;
                        case LEFT:
                            if (anotherLine < line || (anotherLine == line && anotherColumn < column)) {
                                //Swap type
                                if (checkNoIntersection(descriptor, anotherDesc)) {
                                    dispatchHandleStateChange(selHandleType, false);
                                    EditorTouchEventHandler.this.selHandleType = RIGHT;
                                    dispatchHandleStateChange(selHandleType, true);
                                    this.type = RIGHT;
                                    rightHandle.type = LEFT;
                                    SelectionHandle tmp = rightHandle;
                                    rightHandle = leftHandle;
                                    leftHandle = tmp;
                                    editor.setSelectionRegion(anotherLine, anotherColumn, line, column, false, SelectionChangeEvent.CAUSE_SELECTION_HANDLE);
                                }
                            } else {
                                editor.setSelectionRegion(line, column, anotherLine, anotherColumn, false, SelectionChangeEvent.CAUSE_SELECTION_HANDLE);
                            }
                            break;
                    }
                }
            }
        }

    }

    /**
     * Runnable for controlling auto-scrolling when thumb reaches the edges of editor
     */
    private class EdgeScrollRunnable implements Runnable {
        private final static int MAX_FACTOR = 32;
        private final static float INCREASE_FACTOR = 1.06f;

        private final float initialDelta;
        private float deltaHorizontal;
        private float deltaVertical;
        private float lastDx, lastDy;
        private float factorX, factorY;
        private long postTimes;

        public EdgeScrollRunnable(int initDelta) {
            initialDelta = deltaHorizontal = deltaVertical = initDelta;
            postTimes = 0;
        }

        @Override
        public void run() {
            float dx = (((edgeFlags & LEFT_EDGE) != 0) ? -deltaHorizontal : 0) + (((edgeFlags & RIGHT_EDGE) != 0) ? deltaHorizontal : 0);
            float dy = (((edgeFlags & TOP_EDGE) != 0) ? -deltaVertical : 0) + (((edgeFlags & BOTTOM_EDGE) != 0) ? deltaVertical : 0);
            if (dx > 0) {
                // Check whether there is content at right
                int line;
                if (selHandleType == SelectionHandle.BOTH || selHandleType == SelectionHandle.LEFT) {
                    line = editor.getCursor().getLeftLine();
                } else {
                    line = editor.getCursor().getRightLine();
                }
                int column = editor.getText().getColumnCount(line);
                // Do not scroll too far from text region of this line
                float maxOffset = editor.measureTextRegionOffset() + editor.layout.getCharLayoutOffset(line, column)[1] - editor.getWidth() * 0.85f;
                if (scroller.getCurrX() > maxOffset) {
                    dx = 0;
                }
            }
            scrollBy(dx, dy);
            if (magnifier.isShowing()) {
                magnifier.dismiss();
            }

            // Speed up if we are scrolling in the direction
            if (isSameSign(dx, lastDx)) {
                if (factorX < MAX_FACTOR && (postTimes & 1) == 0) {
                    factorX++;
                    deltaHorizontal *= INCREASE_FACTOR;
                }
            } else {
                // Recover initial speed because direction changed
                deltaHorizontal = initialDelta;
                factorX = 0;
            }
            if (isSameSign(dy, lastDy)) {
                if (factorY < MAX_FACTOR && (postTimes & 1) == 0) {
                    factorY++;
                    deltaVertical *= INCREASE_FACTOR;
                }
            } else {
                deltaVertical = initialDelta;
                factorY = 0;
            }
            lastDx = dx;
            lastDy = dy;

            // Update selection
            if (thumbMotionRecord != null)
                handleSelectionChange2(thumbMotionRecord);

            postTimes++;
            // Post for animation
            if (edgeFlags != 0) {
                editor.postDelayedInLifecycle(this, 10);
            }
        }
    }
}

