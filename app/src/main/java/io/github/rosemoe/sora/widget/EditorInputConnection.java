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

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.SurroundingText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.ImePrivateCommandEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ComposingText;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.util.Logger;

/**
 * Connection between input method and editor
 *
 * @author Rosemoe
 */
class EditorInputConnection extends BaseInputConnection {

    private final static Logger logger = Logger.instance("EditorInputConnection");
    static boolean DEBUG = false;
    private final CodeEditor editor;
    protected ComposingText composingText = new ComposingText();
    protected boolean imeConsumingInput = false;
    private boolean connectionInvalid;

    /**
     * Create a connection for the given editor
     *
     * @param targetView Host editor
     */
    public EditorInputConnection(CodeEditor targetView) {
        super(targetView, true);
        editor = targetView;
        connectionInvalid = false;
        targetView.subscribeEvent(ContentChangeEvent.class, (event, __) -> {
            if (event.getAction() == ContentChangeEvent.ACTION_INSERT) {
                composingText.shiftOnInsert(event.getChangeStart().index, event.getChangeEnd().index);
            } else if (event.getAction() == ContentChangeEvent.ACTION_DELETE) {
                composingText.shiftOnDelete(event.getChangeStart().index, event.getChangeEnd().index);
            }
        });
    }

    protected void markInvalid() {
        connectionInvalid = true;
        composingText.reset();
        resetBatchEdit();
        editor.invalidate();
    }

    /**
     * Reset the state of this connection
     */
    protected void reset() {
        resetBatchEdit();
        composingText.reset();
        connectionInvalid = false;
        imeConsumingInput = false;
    }

    private void resetBatchEdit() {
        Content content = editor.getText();
        while (content.isInBatchEdit()) {
            content.endBatchEdit();
        }
    }

    /**
     * Private use.
     * Get the Cursor of Content displaying by Editor
     *
     * @return Cursor
     */
    private Cursor getCursor() {
        return editor.getCursor();
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (DEBUG)
            logger.d("commitText text = " + text + ", pos = " + newCursorPosition);

        if (!editor.isEditable() || connectionInvalid || text == null) {
            return false;
        }

        if ("\n".equals(text.toString())) {
            // #67
            sendKeyClick(KeyEvent.KEYCODE_ENTER);
        } else {
            commitTextInternal(text, true);
        }
        return true;
    }

    @Override
    public synchronized void closeConnection() {
        super.closeConnection();
        resetBatchEdit();
        composingText.reset();
        editor.onCloseConnection();
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        return TextUtils.getCapsMode(editor.getText(), getCursor().getLeft(), reqModes);
    }

    /**
     * Get content region internally
     */
    private CharSequence getTextRegionInternal(int start, int end, int flags, boolean ignoreIPCLimit) {
        var origin = editor.getText();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start < 0) {
            start = 0;
        }
        if (end > origin.length()) {
            end = origin.length();
        }
        if (end < start) {
            start = end = 0;
        }
        if (!ignoreIPCLimit && end - start > editor.getProps().maxIPCTextLength) {
            end = start + Math.max(0, editor.getProps().maxIPCTextLength);
        }
        var sub = origin.subSequence(start, end).toString();
        if (flags == GET_TEXT_WITH_STYLES) {
            var text = new SpannableStringBuilder(sub);
            // Apply composing span
            if (composingText.isComposing()) {
                try {
                    int originalComposingStart = composingText.startIndex;
                    int originalComposingEnd = composingText.endIndex;
                    int transferredStart = originalComposingStart - start;
                    if (transferredStart >= text.length()) {
                        return text;
                    }
                    if (transferredStart < 0) {
                        transferredStart = 0;
                    }
                    int transferredEnd = originalComposingEnd - start;
                    if (transferredEnd <= 0) {
                        return text;
                    }
                    if (transferredEnd >= text.length()) {
                        transferredEnd = text.length();
                    }
                    text.setSpan(Spanned.SPAN_COMPOSING, transferredStart, transferredEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (IndexOutOfBoundsException e) {
                    //ignored
                }
            }
            return text;
        }
        return sub;
    }

    protected CharSequence getTextRegion(int start, int end, int flags) {
        try {
            var res = getTextRegionInternal(start, end, flags, false);
            if (DEBUG)
                logger.d("getTextRegion result:" + res);
            return res;
        } catch (IndexOutOfBoundsException e) {
            logger.w("Failed to get text region for IME", e);
            return "";
        }
    }

    protected CharSequence getTextRegionUnlimited(int start, int end, int flags) {
        try {
            var res = getTextRegionInternal(start, end, flags, true);
            if (DEBUG)
                logger.d("getTextRegion result:" + res);
            return res;
        } catch (IndexOutOfBoundsException e) {
            logger.w("Failed to get text region for IME", e);
            return "";
        }
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        if (editor.getProps().disallowSuggestions) {
            return null;
        }
        //This text should be limited because when the user try to select all text
        //it can be quite large text and costs time, which will finally cause ANR
        int left = getCursor().getLeft();
        int right = getCursor().getRight();
        return left == right ? null : getTextRegion(left, right, flags);
    }

    @Override
    public CharSequence getTextBeforeCursor(int length, int flags) {
        if (editor.getProps().disallowSuggestions) {
            return "";
        }
        int end = getCursor().getLeft();
        int start = Math.max(end - length, end - editor.getProps().maxIPCTextLength);
        return getTextRegion(start, end, flags);
    }

    @Override
    public CharSequence getTextAfterCursor(int length, int flags) {
        if (editor.getProps().disallowSuggestions) {
            return "";
        }
        int end = getCursor().getRight();
        return getTextRegion(end, end + length, flags);
    }

    private void sendKeyClick(int keyCode) {
        long eventTime = SystemClock.uptimeMillis();
        sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    protected void commitTextInternal(@NonNull CharSequence text, boolean applyAutoIndent) {
        var composingStateBefore = composingText.isComposing();
        // NOTE: Text styles are ignored by editor
        // Remove composing text first if there is
        if (editor.getProps().trackComposingTextOnCommit) {
            if (composingText.isComposing()) {
                var composingText = editor.getText().subSequence(this.composingText.startIndex, this.composingText.endIndex).toString();
                var commitText = text.toString();
                if (this.composingText.endIndex == getCursor().getLeft() && !getCursor().isSelected() && commitText.startsWith(composingText) && commitText.length() > composingText.length()) {
                    text = commitText.substring(composingText.length());
                    this.composingText.reset();
                } else {
                    deleteComposingText();
                }
            }
        } else if (composingText.isComposing()) {
            deleteComposingText();
        }

        editor.commitText(text, applyAutoIndent);

        if (composingStateBefore) {
            endBatchEdit();
        }
    }

    /**
     * Delete composing region
     */
    private void deleteComposingText() {
        if (!composingText.isComposing()) {
            return;
        }
        try {
            editor.getText().delete(composingText.startIndex, composingText.endIndex);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        composingText.reset();
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (DEBUG)
            logger.d("deleteSurroundingText, before = " + beforeLength + ", after = " + afterLength);
        if (!editor.isEditable() || connectionInvalid) {
            return false;
        }
        if (beforeLength < 0 || afterLength < 0) {
            return false;
        }

        // #170 Gboard compatible
        if (beforeLength == 1 && afterLength == 0 && !composingText.isComposing()) {
            editor.deleteText();
            return true;
        }

        // Start a batch edit when the operation can not be finished by one call to delete()
        if (beforeLength > 0 && afterLength > 0) {
            beginBatchEdit();
        }

        boolean composing = composingText.isComposing();
        int composingStart = composing ? composingText.startIndex : 0;
        int composingEnd = composing ? composingText.endIndex : 0;

        int rangeEnd = getCursor().getLeft();
        int rangeStart = rangeEnd - beforeLength;
        if (rangeStart < 0) {
            rangeStart = 0;
        }
        editor.getText().delete(rangeStart, rangeEnd);

        if (composing) {
            int crossStart = Math.max(rangeStart, composingStart);
            int crossEnd = Math.min(rangeEnd, composingEnd);
            composingEnd -= Math.max(0, crossEnd - crossStart);
            int delta = Math.max(0, crossStart - rangeStart);
            composingEnd -= delta;
            composingStart -= delta;
        }

        rangeStart = getCursor().getRight();
        rangeEnd = rangeStart + afterLength;
        if (rangeEnd > editor.getText().length()) {
            rangeEnd = editor.getText().length();
        }
        editor.getText().delete(rangeStart, rangeEnd);

        if (composing) {
            int crossStart = Math.max(rangeStart, composingStart);
            int crossEnd = Math.min(rangeEnd, composingEnd);
            composingEnd -= Math.max(0, crossEnd - crossStart);
            int delta = Math.max(0, crossStart - rangeStart);
            composingEnd -= delta;
            composingStart -= delta;
        }

        if (beforeLength > 0 && afterLength > 0) {
            endBatchEdit();
        }

        return true;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        // Unsupported operation
        // According to document, we should return false
        return false;
    }

    @Override
    public synchronized boolean beginBatchEdit() {
        if (DEBUG)
            logger.d("beginBatchEdit");
        if (editor.getProps().disallowSuggestions) {
            return editor.getText().isInBatchEdit(); // Do not start new batch edit layer
        }
        return editor.getText().beginBatchEdit();
    }

    @Override
    public synchronized boolean endBatchEdit() {
        if (DEBUG)
            logger.d("endBatchEdit");
        boolean inBatch = editor.getText().endBatchEdit();
        if (!inBatch) {
            editor.updateSelection();
        }
        return inBatch;
    }

    private void deleteSelected() {
        if (getCursor().isSelected()) {
            // Delete selected text
            editor.deleteText();
        }
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (DEBUG)
            logger.d("setComposingText, text = " + text + ", pos = " + newCursorPosition);
        if (!editor.isEditable() || connectionInvalid || !editor.acceptsComposingText()) {
            return false;
        }
        if (editor.getProps().disallowSuggestions) {
            composingText.reset();
            commitText(text, 0);
            //editor.restartInput();
            return false;
        }
        if (TextUtils.indexOf(text, '\n') != -1) {
            return false;
        }
        if (!composingText.isComposing()) {
            // Create composing info
            deleteSelected();
            beginBatchEdit();
            composingText.preSetComposing = true;
            editor.commitText(text);
            composingText.set(getCursor().getLeft() - text.length(), getCursor().getLeft());
        } else {
            // Already have composing text
            if (composingText.isComposing()) {
                if (editor.getProps().minimizeComposingTextUpdate) {
                    setComposingTextCompat(text.toString());
                } else {
                    editor.getText().replace(composingText.startIndex, composingText.endIndex, text);
                }
                // Reset range
                composingText.adjustLength(text.length());
            }
        }
        if (text.length() == 0) {
            finishComposingText();
            return true;
        }
        return true;
    }

    private void setComposingTextCompat(@NonNull String text) {
        var content = editor.getText();
        String current = content.substring(composingText.startIndex, composingText.endIndex);
        if (current.equals(text)) {
            return;
        }
        if (current.length() < text.length() && text.startsWith(current)) {
            var pos = content.getIndexer().getCharPosition(composingText.endIndex);
            content.insert(pos.line, pos.column, text.substring(current.length()));
        } else if (current.length() > text.length() && current.startsWith(text)) {
            content.delete(composingText.endIndex - (current.length() - text.length()), composingText.endIndex);
        } else {
            content.replace(composingText.startIndex, composingText.endIndex, text);
        }
    }

    @Override
    public boolean finishComposingText() {
        if (DEBUG)
            logger.d("finishComposingText");
        if (!editor.isEditable() || connectionInvalid) {
            return false;
        }
        if (editor.getProps().disallowSuggestions) {
            return false;
        }
        composingText.reset();
        endBatchEdit();
        editor.updateCursor();
        editor.invalidate();
        return true;
    }

    private int getWrappedIndex(int index) {
        if (index < 0) {
            return 0;
        }
        if (index > editor.getText().length()) {
            return editor.getText().length();
        }
        return index;
    }

    @Override
    public boolean setSelection(int start, int end) {
        if (DEBUG)
            logger.d("setSelection, s = " + start + ", e = " + end);
        if (!editor.isEditable() || connectionInvalid || editor.getProps().disallowSuggestions) {
            return false;
        }
        start = getWrappedIndex(start);
        end = getWrappedIndex(end);
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start == getCursor().getLeft() && end == getCursor().getRight()) {
            return true;
        }
        Content content = editor.getText();
        CharPosition startPos = content.getIndexer().getCharPosition(start);
        CharPosition endPos = content.getIndexer().getCharPosition(end);
        editor.setSelectionRegion(startPos.line, startPos.column, endPos.line, endPos.column, false, SelectionChangeEvent.CAUSE_IME);
        return true;
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        if (DEBUG)
            logger.d("setComposingRegion, s = " + start + ", e = " + end);
        if (!editor.isEditable() || connectionInvalid || !editor.acceptsComposingText() || editor.getProps().disallowSuggestions) {
            return false;
        }
        if (start == end) {
            finishComposingText();
            return true;
        }
        try {
            if (start > end) {
                int tmp = start;
                start = end;
                end = tmp;
            }
            if (start < 0) {
                start = 0;
            }
            var content = editor.getText();
            if (end > content.length()) {
                end = content.length();
            }
            if (start >= end) {
                return false;
            }
            composingText.set(start, end);
            editor.invalidate();
        } catch (IndexOutOfBoundsException e) {
            logger.w("set composing region for IME failed", e);
            return false;
        }
        beginBatchEdit();
        return true;
    }

    @Override
    public boolean performContextMenuAction(int id) {
        switch (id) {
            case android.R.id.selectAll:
                editor.selectAll();
                return true;
            case android.R.id.cut:
                editor.copyText();
                if (getCursor().isSelected()) {
                    editor.deleteText();
                }
                return true;
            case android.R.id.paste:
            case android.R.id.pasteAsPlainText:
                editor.pasteText();
                return true;
            case android.R.id.copy:
                editor.copyText();
                return true;
            case android.R.id.undo:
                editor.undo();
                return true;
            case android.R.id.redo:
                editor.redo();
                return true;
        }
        return false;
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        editor.updateCursorAnchor();
        return true;
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        if (DEBUG)
            logger.d("getExtractedText, flags = " + flags);
        if (editor.getProps().disallowSuggestions || editor.getProps().disableTextExtracting) {
            return null;
        }
        if ((flags & GET_EXTRACTED_TEXT_MONITOR) != 0) {
            editor.setExtracting(request);
        } else {
            editor.setExtracting(null);
        }

        return editor.extractText(request);
    }

    @Override
    public boolean clearMetaKeyStates(int states) {
        editor.getKeyMetaStates().clearMetaStates(states);
        return true;
    }

    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        return false;
    }

    @Override
    public Handler getHandler() {
        return editor.getHandler();
    }

    @Nullable
    @Override
    @RequiresApi(31)
    public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        if (DEBUG)
            logger.d("getSurroundingText, beforeLen = " + beforeLength + ", afterLen = " + afterLength);
        if (editor.getProps().disallowSuggestions) {
            return new SurroundingText("", 0, 0, -1);
        }
        if ((beforeLength | afterLength) < 0) {
            throw new IllegalArgumentException("length < 0");
        }
        int startOffset = Math.max(0, getCursor().getLeft() - beforeLength);
        var selStart = getCursor().getLeft();
        startOffset = Math.min(startOffset, selStart);
        var text = getTextRegionUnlimited(startOffset, Math.min(editor.getText().length(), getCursor().getRight() + afterLength), flags);
        return new SurroundingText(text, getCursor().getLeft() - startOffset, getCursor().getRight() - startOffset, startOffset);
    }

    @Override
    public boolean setImeConsumesInput(boolean imeConsumesInput) {
        if (connectionInvalid) {
            return false;
        }
        imeConsumingInput = imeConsumesInput;
        editor.invalidate();
        return true;
    }

    @Override
    public boolean performPrivateCommand(String action, Bundle data) {
        if (connectionInvalid) {
            return false;
        }
        editor.dispatchEvent(new ImePrivateCommandEvent(editor, action, data));
        return true;
    }


}
