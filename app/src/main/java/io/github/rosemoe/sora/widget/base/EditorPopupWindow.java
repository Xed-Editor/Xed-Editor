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
package io.github.rosemoe.sora.widget.base;

import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.github.rosemoe.sora.event.EventReceiver;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Base class for all editor popup windows.
 */
public class EditorPopupWindow {

    /**
     * Update the position of this window when user scrolls the editor
     */
    public final static int FEATURE_SCROLL_AS_CONTENT = 1;

    /**
     * Allow the window to be displayed outside the view's rectangle.
     * Otherwise, the window's size will be adjusted to force it to display in the view.
     * If the space can't display it, it will get hidden.
     */
    public final static int FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED = 1 << 1;

    /**
     * Hide this window when the user scrolls fast. Such as the selection handle
     * is currently near the edge of screen.
     */
    public final static int FEATURE_HIDE_WHEN_FAST_SCROLL = 1 << 2;

    private final PopupWindow window;
    private final CodeEditor editor;
    private final int features;
    private final int[] locationBuffer = new int[2];
    private final EventReceiver<ScrollEvent> scrollListener;
    private boolean registerFlag;
    private boolean registered;
    private View parentView;
    private int offsetX, offsetY, windowX, windowY, width, height;

    /**
     * Create a popup window for editor
     *
     * @param features Features to request
     * @see #FEATURE_SCROLL_AS_CONTENT
     * @see #FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED
     * @see #FEATURE_HIDE_WHEN_FAST_SCROLL
     */
    public EditorPopupWindow(@NonNull CodeEditor editor, int features) {
        this.editor = Objects.requireNonNull(editor);
        this.features = features;
        parentView = editor;
        window = new PopupWindow();
        window.setElevation(editor.getDpUnit() * 8);
        scrollListener = ((event, unsubscribe) -> {
            if (!registerFlag) {
                unsubscribe.unsubscribe();
                registered = false;
                return;
            }
            switch (event.getCause()) {
                case ScrollEvent.CAUSE_MAKE_POSITION_VISIBLE:
                case ScrollEvent.CAUSE_TEXT_SELECTING:
                case ScrollEvent.CAUSE_USER_FLING:
                case ScrollEvent.CAUSE_SCALE_TEXT:
                    if (isFeatureEnabled(FEATURE_HIDE_WHEN_FAST_SCROLL) &&
                            (Math.abs(event.getEndX() - event.getStartX()) > 80 ||
                                    Math.abs(event.getEndY() - event.getStartY()) > 80)) {
                        if (isShowing()) {
                            dismiss();
                            return;
                        }
                    }
                    break;
            }
            if (isFeatureEnabled(FEATURE_SCROLL_AS_CONTENT)) {
                applyWindowAttributes(false);
            }
        });
        register();
    }

    /**
     * Get editor instance
     */
    @NonNull
    public CodeEditor getEditor() {
        return editor;
    }

    /**
     * Checks whether a single feature is enabled
     *
     * @see #FEATURE_SCROLL_AS_CONTENT
     * @see #FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED
     * @see #FEATURE_HIDE_WHEN_FAST_SCROLL
     */
    public boolean isFeatureEnabled(int feature) {
        if (Integer.bitCount(feature) != 1) {
            throw new IllegalArgumentException("Not a valid feature integer");
        }
        return (features & feature) != 0;
    }

    /**
     * Register this window in target editor.
     * After registering, features are available.
     * This automatically done when you create the window. But if you call {@link #unregister()}, you
     * should re-invoke this method to make features available.
     */
    public void register() {
        if (!registered) {
            editor.subscribeEvent(ScrollEvent.class, scrollListener);
        }
        registerFlag = true;
    }

    /**
     * Unregister this window in target editor.
     */
    public void unregister() {
        registerFlag = false;
    }

    public boolean isShowing() {
        return getPopup().isShowing();
    }

    /**
     * Get the actual {@link PopupWindow} instance.
     * <p>
     * Note that you should not manage its visibility but update that by invoking methods in this
     * class. Otherwise, there may be some abnormal display.
     */
    @NonNull
    public PopupWindow getPopup() {
        return window;
    }

    /**
     * @see PopupWindow#setContentView(View)
     */
    public void setContentView(View view) {
        window.setContentView(view);
    }

    private int wrapHorizontal(int horizontal) {
        return Math.max(0, Math.min(horizontal, editor.getWidth()));
    }

    private int wrapVertical(int vertical) {
        return Math.max(0, Math.min(vertical, editor.getHeight()));
    }

    private void applyWindowAttributes(boolean show) {
        if (!show && !isShowing()) {
            return;
        }
        boolean autoScroll = isFeatureEnabled(FEATURE_SCROLL_AS_CONTENT);
        var left = autoScroll ? (windowX - editor.getOffsetX()) : (windowX - offsetX);
        var top = autoScroll ? (windowY - editor.getOffsetY()) : (windowY - offsetY);
        var right = left + width;
        var bottom = top + height;
        if (!isFeatureEnabled(FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED)) {
            // Adjust positions
            left = wrapHorizontal(left);
            right = wrapHorizontal(right);
            top = wrapVertical(top);
            bottom = wrapVertical(bottom);
            if (top >= bottom || left >= right) {
                dismiss();
                return;
            }
        }
        // Show/update if needed
        editor.getLocationInWindow(locationBuffer);
        int width = right - left;
        int height = bottom - top;
        left += locationBuffer[0];
        top += locationBuffer[1];
        if (window.isShowing()) {
            window.update(left, top, width, height);
        } else if (show) {
            window.setHeight(height);
            window.setWidth(width);
            window.showAtLocation(parentView, Gravity.START | Gravity.TOP, left, top);
        }
    }

    /**
     * Get width you've set for this window.
     * <p>
     * Note that, according to you feature switches, this may be different from the actual size of the window on screen.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get height you've set for this window.
     * <p>
     * Note that, according to you feature switches, this may be different from the actual size of the window on screen.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Set the size of this window.
     * <p>
     * Note that, according to you feature switches, the window can have a different size on screen.
     */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        applyWindowAttributes(false);
    }

    /**
     * Sets the position of the window <strong>in editor's drawing offset</strong>
     */
    public void setLocation(int x, int y) {
        windowX = x;
        windowY = y;
        offsetY = getEditor().getOffsetY();
        offsetX = getEditor().getOffsetX();
        applyWindowAttributes(false);
    }

    /**
     * Sets the absolute position on view.
     */
    public void setLocationAbsolutely(int x, int y) {
        setLocation(x + editor.getOffsetX(), y + editor.getOffsetY());
    }

    /**
     * Show the window if appropriate
     */
    public void show() {
        if (isShowing()) {
            return;
        }
        applyWindowAttributes(true);
    }

    /**
     * Dismiss the window
     */
    public void dismiss() {
        if (isShowing()) {
            window.dismiss();
        }
    }

    @NonNull
    public View getParentView() {
        return parentView;
    }

    /**
     * Set parent view of popup.
     *
     * @param view View for {@link PopupWindow#showAtLocation(View, int, int, int)}
     */
    public void setParentView(@NonNull View view) {
        parentView = Objects.requireNonNull(view);
    }
}
