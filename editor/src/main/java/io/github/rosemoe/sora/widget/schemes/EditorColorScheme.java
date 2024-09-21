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
package io.github.rosemoe.sora.widget.schemes;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.rosemoe.sora.annotations.UnsupportedUserUsage;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * This class manages the colors of editor.
 * You can use color IDs that are not in pre-defined id pool for custom languages. We recommend
 *  adding a base offset for your custom color IDs. For example, first custom color ID is 256. This
 *   leaves enough space for editor's future built-in colors.
 * <p>
 * This is also the default color scheme of editor.
 * Be careful to change this class, because this can cause its
 * subclasses behave differently and some subclasses did not apply
 * their default colors to some color ids. So change to this can cause
 * sub themes to change as well.
 * <p>
 * Typically, you can use this class to set color of editor directly
 * with {@link #setColor(int, int)} in a thread with looper.
 * <p>
 * However, we also accept you to extend this class to customize
 * your own ColorScheme to use different default colors.
 * Subclasses are expected to override {@link #applyDefault()}
 * to define colors, though other methods are not final.
 * After overriding this method, you will have to call super class's
 * applyDefault() and then a series of {@link #setColor(int, int)} calls
 * to apply your colors.
 * <p>
 * Note that new colors can be added in newer version of editor,
 * it is dangerous not to call super.applyDefault(), which can cause
 * newer editor works wrongly.
 * <p>
 * For more pre-defined color schemes, please turn to package io.github.rosemoe.editor.widget.schemes
 * <p>
 * Thanks to liyujiang-gzu (GitHub @liyujiang-gzu) for contribution to color schemes
 *
 * @author Rose
 */
public class EditorColorScheme {

    //----------------Issue colors----------------
    public static final int PROBLEM_TYPO = 37;
    public static final int PROBLEM_WARNING = 36;
    public static final int PROBLEM_ERROR = 35;
    //-----------------Highlight colors-----------
    public static final int ATTRIBUTE_VALUE = 34;
    public static final int ATTRIBUTE_NAME = 33;
    public static final int HTML_TAG = 32;
    public static final int ANNOTATION = 28;
    public static final int FUNCTION_NAME = 27;
    public static final int IDENTIFIER_NAME = 26;
    public static final int IDENTIFIER_VAR = 25;
    public static final int LITERAL = 24;
    public static final int OPERATOR = 23;
    public static final int COMMENT = 22;
    public static final int KEYWORD = 21;
    //-------------View colors---------------------
    public static final int STICKY_SCROLL_DIVIDER = 62;
    /**
     * Color for text strikethrough. If value is 0, text color of that region will be used.
     */
    public static final int STRIKETHROUGH = 57;
    /**
     * Alias for {@link #STRIKETHROUGH}
     */
    public static final int STRIKE_THROUGH = STRIKETHROUGH;
    public static final int DIAGNOSTIC_TOOLTIP_ACTION = 56;
    public static final int DIAGNOSTIC_TOOLTIP_DETAILED_MSG = 55;
    public static final int DIAGNOSTIC_TOOLTIP_BRIEF_MSG = 54;
    public static final int DIAGNOSTIC_TOOLTIP_BACKGROUND = 53;
    public static final int FUNCTION_CHAR_BACKGROUND_STROKE = 52;
    public static final int HARD_WRAP_MARKER = 51;
    public static final int TEXT_INLAY_HINT_FOREGROUND = 50;
    public static final int TEXT_INLAY_HINT_BACKGROUND = 49;
    public static final int SNIPPET_BACKGROUND_EDITING = 48;
    public static final int SNIPPET_BACKGROUND_RELATED = 47;
    public static final int SNIPPET_BACKGROUND_INACTIVE = 46;
    public static final int SIDE_BLOCK_LINE = 38;
    public static final int NON_PRINTABLE_CHAR = 31;

    /**
     * Use zero if the text color should not be changed
     */
    public static final int TEXT_SELECTED = 30;
    public static final int MATCHED_TEXT_BACKGROUND = 29;
    public static final int COMPLETION_WND_CORNER = 20;
    public static final int COMPLETION_WND_BACKGROUND = 19;
    public static final int COMPLETION_WND_TEXT_PRIMARY = 42;
    public static final int COMPLETION_WND_TEXT_SECONDARY = 43;
    public static final int COMPLETION_WND_ITEM_CURRENT = 44;

    /**
     * No longer supported
     */
    public static final int LINE_BLOCK_LABEL = 18;

    public static final int HIGHLIGHTED_DELIMITERS_BACKGROUND = 41;
    public static final int HIGHLIGHTED_DELIMITERS_UNDERLINE = 40;
    public static final int HIGHLIGHTED_DELIMITERS_FOREGROUND = 39;
    public static final int LINE_NUMBER_PANEL_TEXT = 17;
    public static final int LINE_NUMBER_PANEL = 16;
    public static final int BLOCK_LINE_CURRENT = 15;
    public static final int BLOCK_LINE = 14;
    public static final int SCROLL_BAR_TRACK = 13;
    public static final int SCROLL_BAR_THUMB_PRESSED = 12;
    public static final int SCROLL_BAR_THUMB = 11;
    public static final int UNDERLINE = 10;
    public static final int CURRENT_LINE = 9;
    public static final int SELECTION_HANDLE = 8;
    public static final int SELECTION_INSERT = 7;
    public static final int SELECTED_TEXT_BACKGROUND = 6;
    public static final int TEXT_NORMAL = 5;
    public static final int WHOLE_BACKGROUND = 4;
    public static final int LINE_NUMBER_BACKGROUND = 3;
    public static final int LINE_NUMBER_CURRENT = 45;
    public static final int LINE_NUMBER = 2;
    public static final int LINE_DIVIDER = 1;

    /**
     * Min pre-defined color id
     */
    protected static final int START_COLOR_ID = 1;

    public static final int SIGNATURE_TEXT_NORMAL = 58;
    public static final int SIGNATURE_TEXT_HIGHLIGHTED_PARAMETER = 59;

    public static final int STATIC_SPAN_BACKGROUND = 63;
    public static final int STATIC_SPAN_FOREGROUND = 64;

    public static final int SIGNATURE_BACKGROUND = 60;

    /**
     * Max pre-defined color id
     */
    protected static final int END_COLOR_ID = 64;


    /**
     * Real color saver
     */
    protected final SparseIntArray colors;
    /**
     * Host editor object
     */
    private final List<WeakReference<CodeEditor>> editors;

    private final boolean dark;

    private final static int PRIMARY_TEXT_COLOR_DEFAULT_LIGHT = 0xff424242;
    private final static int PRIMARY_TEXT_COLOR_DEFAULT_DARK = 0xfff5f5f5;
    private final static int BACKGROUND_COLOR_LIGHT = 0xfffefefe;
    private final static int BACKGROUND_COLOR_DARK = 0xff212121;
    private final static int SECONDARY_TEXT_COLOR_LIGHT = 0xff616161;
    private final static int SECONDARY_TEXT_COLOR_DARK = 0xffeeeeee;

    /**
     * Create a new ColorScheme for the given editor
     *
     * @param editor Host editor
     */
    public EditorColorScheme(CodeEditor editor) {
        this();
        attachEditor(editor);
    }

    /**
     * Create a default color scheme
     */
    public EditorColorScheme() {
        this(false);
    }

    /**
     * For subclass
     *
     * @param isDark Whether this is a dark theme
     */
    protected EditorColorScheme(boolean isDark) {
        colors = new SparseIntArray();
        editors = new ArrayList<>();
        this.dark = isDark;
        applyDefault();
    }

    /**
     * Subscribe changes
     * <p>
     * Called by editor
     */
    @UnsupportedUserUsage
    public void attachEditor(@NonNull CodeEditor editor) {
        Objects.requireNonNull(editor);
        for (var ref : editors) {
            if (ref.get() == editor) {
                return;
            }
        }
        editors.add(new WeakReference<>(editor));
        editor.onColorFullUpdate();
    }

    /**
     * Unsubscribe changes
     */
    @UnsupportedUserUsage
    public void detachEditor(@NonNull CodeEditor editor) {
        var itr = editors.iterator();
        while (itr.hasNext()) {
            if (itr.next().get() == editor) {
                itr.remove();
                break;
            }
        }
    }

    /**
     * Apply default colors
     */
    public void applyDefault() {
        for (int i = START_COLOR_ID; i <= END_COLOR_ID; i++) {
            applyDefault(i);
        }
    }

    /**
     * Apply default color for the given type
     *
     * @param type The type
     */
    private void applyDefault(int type) {
        int color = colors.get(type);
        switch (type) {
            case LINE_NUMBER:
            case LINE_NUMBER_CURRENT:
                color = 0xFF505050;
                break;
            case LINE_NUMBER_BACKGROUND:
            case LINE_DIVIDER:
                color = 0xeeeeeeee;
                break;
            case STATIC_SPAN_BACKGROUND:
            case WHOLE_BACKGROUND:
            case LINE_NUMBER_PANEL_TEXT:
            case COMPLETION_WND_BACKGROUND:
            case COMPLETION_WND_CORNER:
                color = isDark() ? BACKGROUND_COLOR_DARK : 0xffffffff;
                break;
            case OPERATOR:
                color = 0xFF0066D6;
                break;
            case STATIC_SPAN_FOREGROUND:
            case TEXT_NORMAL:
                color = 0xFF333333;
                break;
            case SELECTION_INSERT:
                color = 0xdd536dfe;
                break;
            case UNDERLINE:
                color = 0xff000000;
                break;
            case SELECTION_HANDLE:
                color = 0xff536dfe;
                break;
            case ANNOTATION:
            case SIGNATURE_TEXT_HIGHLIGHTED_PARAMETER:
            case IDENTIFIER_NAME:
                color = 0xFF03A9F4;
                break;
            case CURRENT_LINE:
                color = 0x10000000;
                break;
            case SELECTED_TEXT_BACKGROUND:
            case FUNCTION_CHAR_BACKGROUND_STROKE:
                color = 0x2D3F51B5;
                break;
            case KEYWORD:
                color = 0xFF2196F3;
                break;
            case COMMENT:
                color = 0xffa8a8a8;
                break;
            case LITERAL:
                color = 0xFF008080;
                break;
            case SCROLL_BAR_THUMB:
                color = 0xffd8d8d8;
                break;
            case SCROLL_BAR_THUMB_PRESSED:
                color = 0xFF27292A;
                break;
            case BLOCK_LINE:
                color = 0xffdddddd;
                break;
            case LINE_BLOCK_LABEL:
            case SCROLL_BAR_TRACK:
            case TEXT_SELECTED:
            case STRIKETHROUGH:
                color = 0;
                break;
            case LINE_NUMBER_PANEL:
                color = 0xdd000000;
                break;
            case BLOCK_LINE_CURRENT:
            case SIDE_BLOCK_LINE:
                color = 0xff999999;
                break;
            case IDENTIFIER_VAR:
                color = 0xff546e7a;
                break;
            case FUNCTION_NAME:
                color = 0xffe040fb;
                break;
            case MATCHED_TEXT_BACKGROUND:
                color = 0xffffff00;
                break;
            case NON_PRINTABLE_CHAR:
                color = 0xeecccccc;
                break;
            case PROBLEM_ERROR:
                color = 0xaaff0000;
                break;
            case PROBLEM_WARNING:
                color = 0xaafff100;
                break;
            case PROBLEM_TYPO:
                color = 0x6600ff11;
                break;
            case HIGHLIGHTED_DELIMITERS_FOREGROUND:
                color = 0xdd000000;
                break;
            case HIGHLIGHTED_DELIMITERS_UNDERLINE:
                color = 0xff3f51b5;
                break;
            case HIGHLIGHTED_DELIMITERS_BACKGROUND:
                color = 0x1D000000;
                break;
            case COMPLETION_WND_TEXT_PRIMARY:
            case COMPLETION_WND_TEXT_SECONDARY:
            case TEXT_INLAY_HINT_FOREGROUND:
                color = isDark() ? 0xffffffff : 0xff000000;
                break;
            case COMPLETION_WND_ITEM_CURRENT:
                color = 0xffeeeeee;
                break;
            case SNIPPET_BACKGROUND_EDITING:
                color = 0xffcccccc;
                break;
            case SNIPPET_BACKGROUND_RELATED:
                color = 0xaadddddd;
                break;
            case SNIPPET_BACKGROUND_INACTIVE:
                color = 0x66dddddd;
                break;
            case SIGNATURE_TEXT_NORMAL:
                color = isDark() ? 0xffeeeeee : 0xff000000;
                break;
            case STICKY_SCROLL_DIVIDER:
                color = isDark() ? 0x99eeeeee : 0x99000000;
                break;
            case TEXT_INLAY_HINT_BACKGROUND:
                color = isDark() ? 0xffeeeeee : 0x1D000000;
                break;
            case HARD_WRAP_MARKER:
                color = !isDark() ? 0xffeeeeee : 0x1D000000;
                break;
            case DIAGNOSTIC_TOOLTIP_BRIEF_MSG:
                color = isDark() ? PRIMARY_TEXT_COLOR_DEFAULT_DARK : PRIMARY_TEXT_COLOR_DEFAULT_LIGHT;
                break;
            case DIAGNOSTIC_TOOLTIP_DETAILED_MSG:
                color = isDark() ? SECONDARY_TEXT_COLOR_DARK : SECONDARY_TEXT_COLOR_LIGHT;
                break;
            case SIGNATURE_BACKGROUND:
            case DIAGNOSTIC_TOOLTIP_BACKGROUND:
                color = isDark() ? BACKGROUND_COLOR_DARK : BACKGROUND_COLOR_LIGHT;
                break;
            case DIAGNOSTIC_TOOLTIP_ACTION:
                color = 0xff42A5F5;
        }
        setColor(type, color);
    }

    /**
     * Apply a new color for the given type
     *
     * @param type  The type
     * @param color New color
     */
    public void setColor(int type, int color) {
        //Do not change if the old value is the same as new value
        //due to avoid unnecessary invalidate() calls
        int old = getColor(type);
        if (old == color) {
            return;
        }

        colors.put(type, color);

        //Notify the editor
        var itr = editors.iterator();
        while (itr.hasNext()) {
            var editor = itr.next().get();
            if (editor == null) {
                itr.remove();
            } else {
                editor.onColorUpdated(type);
            }
        }
    }

    /**
     * Get color by type
     *
     * @param type The type
     * @return The color for type
     */
    public int getColor(int type) {
        return colors.get(type);
    }

    /**
     * Check whether this color scheme is dark
     */
    public boolean isDark() {
        return dark;
    }

    private static EditorColorScheme globalDefault = new EditorColorScheme();

    /**
     * Get global default color scheme.
     */
    @NonNull
    public static EditorColorScheme getDefault() {
        return globalDefault;
    }

    /**
     * Set global default color scheme. Newly created editor will use the new default color scheme.
     *
     * @param colorScheme new global color scheme, or null for restoring to built-in default
     */
    public static void setDefault(@Nullable EditorColorScheme colorScheme) {
        setDefault(colorScheme, false);
    }

    /**
     * Set global default color scheme and optionally update existing editors that are using default
     * color scheme.
     *
     * @param colorScheme   new global color scheme, or null for restoring to built-in default
     * @param updateEditors update existing editors that are using default color scheme
     */
    public static void setDefault(@Nullable EditorColorScheme colorScheme, boolean updateEditors) {
        if (colorScheme == null) {
            colorScheme = new EditorColorScheme();
        }
        if (updateEditors) {
            var editors = globalDefault.editors.toArray(new WeakReference[0]);
            for (var ref : editors) {
                var editor = (CodeEditor) ref.get();
                if (editor != null) {
                    editor.setColorScheme(colorScheme);
                }
            }
        }
        globalDefault = colorScheme;
    }

}
