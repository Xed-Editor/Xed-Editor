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
package io.github.rosemoe.sora.text;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for Content to take down modification
 * As well as provide Undo/Redo actions
 *
 * @author Rosemoe
 */
public final class UndoManager implements ContentListener, Parcelable {

    public final static Creator<UndoManager> CREATOR = new Creator<>() {
        @Override
        public UndoManager createFromParcel(Parcel parcel) {
            var o = new UndoManager();
            o.maxStackSize = parcel.readInt();
            o.stackPointer = parcel.readInt();
            o.undoEnabled = parcel.readInt() > 0;
            var count = parcel.readInt();
            while (count > 0) {
                o.actionStack.add(parcel.readParcelable(UndoManager.class.getClassLoader()));
                count--;
            }
            return o;
        }

        @Override
        public UndoManager[] newArray(int flags) {
            return new UndoManager[flags];
        }
    };
    /**
     * The max time span limit for merging actions
     */
    private static long sMergeTimeLimit = 8000L;
    private final List<ContentAction> actionStack;
    private boolean undoEnabled;
    private int maxStackSize;
    private InsertAction insertAction;
    private DeleteAction deleteAction;
    private Content targetContent;
    private boolean replaceMark;
    private int stackPointer;
    private boolean ignoreModification;
    private boolean forceNewMultiAction;
    private TextRange memorizedCursorRange;

    /**
     * Create an UndoManager
     */
    UndoManager() {
        actionStack = new ArrayList<>();
        replaceMark = false;
        insertAction = null;
        deleteAction = null;
        stackPointer = 0;
        ignoreModification = false;
    }

    /**
     * @see #setMergeTimeLimit(long)
     */
    public static long getMergeTimeLimit() {
        return sMergeTimeLimit;
    }

    /**
     * Set max time span limit for merging actions
     *
     * @param mergeTimeLimit Time in millisecond
     */
    public static void setMergeTimeLimit(long mergeTimeLimit) {
        UndoManager.sMergeTimeLimit = mergeTimeLimit;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(maxStackSize);
        parcel.writeInt(stackPointer);
        parcel.writeInt(undoEnabled ? 1 : 0);
        parcel.writeInt(actionStack.size());
        for (ContentAction contentAction : actionStack) {
            parcel.writeParcelable(contentAction, flags);
        }
    }

    /**
     * Check whether we are currently in undo/redo operations
     */
    public boolean isModifyingContent() {
        return ignoreModification;
    }

    /**
     * Undo on the given Content
     *
     * @param content Undo Target
     */
    @Nullable
    public TextRange undo(Content content) {
        if (canUndo() && !isModifyingContent()) {
            ignoreModification = true;
            var action = actionStack.get(stackPointer - 1);
            action.undo(content);
            stackPointer--;
            ignoreModification = false;
            return action.cursor;
        }
        return null;
    }

    /**
     * Redo on the given Content
     *
     * @param content Redo Target
     */
    public void redo(Content content) {
        if (canRedo() && !isModifyingContent()) {
            ignoreModification = true;
            actionStack.get(stackPointer).redo(content);
            stackPointer++;
            ignoreModification = false;
        }
    }

    void onExitBatchEdit() {
        forceNewMultiAction = true;
        if (!actionStack.isEmpty() && actionStack.get(actionStack.size() - 1) instanceof MultiAction) {
            var action = ((MultiAction) actionStack.get(actionStack.size() - 1));
            if (action._actions.size() == 1) {
                actionStack.set(actionStack.size() - 1, action._actions.get(0));
            }
        }
    }

    /**
     * Whether it can undo
     */
    public boolean canUndo() {
        return isUndoEnabled() && (stackPointer > 0);
    }

    /**
     * Whether it can redo
     */
    public boolean canRedo() {
        return isUndoEnabled() && (stackPointer < actionStack.size());
    }

    /**
     * Whether this UndoManager is enabled
     *
     * @return Whether enabled
     */
    public boolean isUndoEnabled() {
        return undoEnabled;
    }

    /**
     * Set whether enable this module
     *
     * @param enabled Enable or disable
     */
    public void setUndoEnabled(boolean enabled) {
        undoEnabled = enabled;
        if (!enabled) {
            cleanStack();
        }
    }

    /**
     * Get current max stack size
     *
     * @return max stack size
     */
    public int getMaxUndoStackSize() {
        return maxStackSize;
    }

    /**
     * Set a max stack size for this UndoManager
     *
     * @param maxSize max stack size
     */
    public void setMaxUndoStackSize(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException(
                    "max size can not be zero or smaller.Did you want to disable undo module by calling setUndoEnabled()?");
        }
        maxStackSize = maxSize;
        cleanStack();
    }

    /**
     * Clean stack after add or state change
     * This is to limit stack size
     */
    private void cleanStack() {
        if (!undoEnabled) {
            actionStack.clear();
            stackPointer = 0;
        } else {
            while (stackPointer > 1 && actionStack.size() > maxStackSize) {
                actionStack.remove(0);
                stackPointer--;
            }
        }
    }

    /**
     * Clean the stack before pushing
     * If we are not at the end(Undo action executed),remove those actions
     */
    private void cleanBeforePush() {
        while (stackPointer < actionStack.size()) {
            actionStack.remove(actionStack.size() - 1);
        }
    }

    /**
     * Push a new {@link ContentAction} to stack
     * It will merge actions if possible
     *
     * @param action New {@link ContentAction}
     */
    private void pushAction(Content content, ContentAction action) {
        if (!isUndoEnabled()) {
            return;
        }
        cleanBeforePush();
        if (content.isInBatchEdit()) {
            if (actionStack.isEmpty()) {
                MultiAction a = new MultiAction();
                a.addAction(action);
                a.cursor = action.cursor;
                actionStack.add(a);
                stackPointer++;
            } else {
                ContentAction a = actionStack.get(actionStack.size() - 1);
                if (a instanceof MultiAction && !forceNewMultiAction) {
                    MultiAction ac = (MultiAction) a;
                    ac.addAction(action);
                } else {
                    MultiAction ac = new MultiAction();
                    ac.addAction(action);
                    ac.cursor = action.cursor;
                    actionStack.add(ac);
                    stackPointer++;
                }
            }
        } else {
            if (actionStack.isEmpty()) {
                actionStack.add(action);
                stackPointer++;
            } else {
                ContentAction last = actionStack.get(actionStack.size() - 1);
                if (last.canMerge(action)) {
                    last.merge(action);
                } else {
                    actionStack.add(action);
                    stackPointer++;
                }
            }
        }
        forceNewMultiAction = false;
        cleanStack();
    }

    public void exitReplaceMode() {
        if (replaceMark && deleteAction != null) {
            pushAction(targetContent, deleteAction);
        }
        replaceMark = false;
        targetContent = null;
    }

    @Override
    public void beforeReplace(@NonNull Content content) {
        if (ignoreModification) {
            return;
        }
        replaceMark = true;
        targetContent = content;
    }

    @Override
    public void afterInsert(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn,
                            @NonNull CharSequence insertedContent) {
        if (ignoreModification) {
            return;
        }
        insertAction = new InsertAction();
        insertAction.startLine = startLine;
        insertAction.startColumn = startColumn;
        insertAction.endLine = endLine;
        insertAction.endColumn = endColumn;
        insertAction.text = insertedContent;
        if (replaceMark && deleteAction != null) {
            ReplaceAction rep = new ReplaceAction();
            rep.delete = deleteAction;
            rep.insert = insertAction;
            rep.cursor = memorizedCursorRange;
            pushAction(content, rep);
        } else {
            insertAction.cursor = memorizedCursorRange;
            pushAction(content, insertAction);
        }
        deleteAction = null;
        insertAction = null;
        replaceMark = false;
    }

    @Override
    public void afterDelete(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn,
                            @NonNull CharSequence deletedContent) {
        if (ignoreModification) {
            return;
        }
        deleteAction = new DeleteAction();
        deleteAction.endColumn = endColumn;
        deleteAction.startColumn = startColumn;
        deleteAction.endLine = endLine;
        deleteAction.startLine = startLine;
        deleteAction.text = deletedContent;
        deleteAction.cursor = memorizedCursorRange;
        if (!replaceMark) {
            pushAction(content, deleteAction);
        }
    }

    @Override
    public void beforeModification(@NonNull Content content) {
        if (!undoEnabled || !content.isCursorCreated() || replaceMark && deleteAction != null) {
            return;
        }
        var cursor = content.getCursor();
        memorizedCursorRange = cursor.getRange();
    }

    /**
     * Base class of content actions
     *
     * @author Rosemoe
     */
    public static abstract class ContentAction implements Parcelable {

        public transient TextRange cursor;

        /**
         * Undo this action
         *
         * @param content On the given object
         */
        public abstract void undo(Content content);

        /**
         * Redo this action
         *
         * @param content On the given object
         */
        public abstract void redo(Content content);

        /**
         * Get whether the target action can be merged with this action
         *
         * @param action Target action to merge
         * @return Whether they can merge
         */
        public abstract boolean canMerge(ContentAction action);

        /**
         * Merge with target action
         *
         * @param action Target action to merge
         */
        public abstract void merge(ContentAction action);

    }

    /**
     * Insert action model for UndoManager
     *
     * @author Rosemoe
     */
    public static final class InsertAction extends ContentAction {

        public static final Creator<InsertAction> CREATOR = new Creator<>() {
            @Override
            public InsertAction createFromParcel(Parcel parcel) {
                var o = new InsertAction();
                o.startLine = parcel.readInt();
                o.startColumn = parcel.readInt();
                o.endLine = parcel.readInt();
                o.endColumn = parcel.readInt();
                o.text = parcel.readString();
                return o;
            }

            @Override
            public InsertAction[] newArray(int size) {
                return new InsertAction[size];
            }
        };
        public int startLine, endLine, startColumn, endColumn;
        public transient long createTime = System.currentTimeMillis();
        public CharSequence text;

        @Override
        public void undo(Content content) {
            content.delete(startLine, startColumn, endLine, endColumn);
        }

        @Override
        public void redo(Content content) {
            content.insert(startLine, startColumn, text);
        }

        @Override
        public boolean canMerge(ContentAction action) {
            if (action instanceof InsertAction) {
                InsertAction ac = (InsertAction) action;
                return (ac.startColumn == endColumn && ac.startLine == endLine
                        && ac.text.length() + text.length() < 10000
                        && Math.abs(ac.createTime - createTime) < sMergeTimeLimit);
            }
            return false;
        }

        @Override
        public void merge(ContentAction action) {
            if (!canMerge(action)) {
                throw new IllegalArgumentException();
            }
            InsertAction ac = (InsertAction) action;
            this.endColumn = ac.endColumn;
            this.endLine = ac.endLine;
            StringBuilder sb;
            if (text instanceof StringBuilder) {
                sb = (StringBuilder) text;
            } else {
                sb = new StringBuilder(text);
                text = sb;
            }
            sb.append(ac.text);
        }

        @NonNull
        @Override
        public String toString() {
            return "InsertAction{" +
                    "startLine=" + startLine +
                    ", endLine=" + endLine +
                    ", startColumn=" + startColumn +
                    ", endColumn=" + endColumn +
                    ", createTime=" + createTime +
                    ", text=" + text +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(startLine);
            parcel.writeInt(startColumn);
            parcel.writeInt(endLine);
            parcel.writeInt(endColumn);
            parcel.writeString(text.toString());
        }
    }

    /**
     * MultiAction saves several actions for UndoManager
     *
     * @author Rose
     */
    public static final class MultiAction extends ContentAction {

        public final static Creator<MultiAction> CREATOR = new Creator<>() {
            @Override
            public MultiAction createFromParcel(Parcel parcel) {
                var o = new MultiAction();
                var count = parcel.readInt();
                while (count > 0) {
                    o._actions.add(parcel.readParcelable(MultiAction.class.getClassLoader()));
                    count--;
                }
                return o;
            }

            @Override
            public MultiAction[] newArray(int size) {
                return new MultiAction[size];
            }
        };
        private final List<ContentAction> _actions = new ArrayList<>();

        public void addAction(ContentAction action) {
            if (_actions.isEmpty()) {
                _actions.add(action);
            } else {
                ContentAction last = _actions.get(_actions.size() - 1);
                if (last.canMerge(action)) {
                    last.merge(action);
                } else {
                    _actions.add(action);
                }
            }
        }

        @Override
        public void undo(Content content) {
            for (int i = _actions.size() - 1; i >= 0; i--) {
                _actions.get(i).undo(content);
            }
        }

        @Override
        public void redo(Content content) {
            for (int i = 0; i < _actions.size(); i++) {
                _actions.get(i).redo(content);
            }
        }

        @Override
        public boolean canMerge(ContentAction action) {
            return false;
        }

        @Override
        public void merge(ContentAction action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(_actions.size());
            for (ContentAction action : _actions) {
                parcel.writeParcelable(action, flags);
            }
        }
    }

    /**
     * Delete action model for UndoManager
     *
     * @author Rose
     */
    public static final class DeleteAction extends ContentAction {

        public final static Creator<DeleteAction> CREATOR = new Creator<>() {
            @Override
            public DeleteAction createFromParcel(Parcel parcel) {
                var o = new DeleteAction();
                o.startLine = parcel.readInt();
                o.startColumn = parcel.readInt();
                o.endLine = parcel.readInt();
                o.endColumn = parcel.readInt();
                o.text = parcel.readString();
                return o;
            }

            @Override
            public DeleteAction[] newArray(int size) {
                return new DeleteAction[size];
            }
        };
        public int startLine, endLine, startColumn, endColumn;
        public transient long createTime = System.currentTimeMillis();
        public CharSequence text;

        @Override
        public void undo(Content content) {
            content.insert(startLine, startColumn, text);
        }

        @Override
        public void redo(Content content) {
            content.delete(startLine, startColumn, endLine, endColumn);
        }

        @Override
        public boolean canMerge(ContentAction action) {
            if (action instanceof DeleteAction) {
                DeleteAction ac = (DeleteAction) action;
                return (ac.endColumn == startColumn && ac.endLine == startLine
                        && ac.text.length() + text.length() < 10000
                        && Math.abs(ac.createTime - createTime) < sMergeTimeLimit);
            }
            return false;
        }

        @Override
        public void merge(ContentAction action) {
            if (!canMerge(action)) {
                throw new IllegalArgumentException();
            }
            DeleteAction ac = (DeleteAction) action;
            this.startColumn = ac.startColumn;
            this.startLine = ac.startLine;
            StringBuilder sb;
            if (text instanceof StringBuilder) {
                sb = (StringBuilder) text;
            } else {
                sb = new StringBuilder(text);
                text = sb;
            }
            sb.insert(0, ac.text);
        }

        @NonNull
        @Override
        public String toString() {
            return "DeleteAction{" +
                    "startLine=" + startLine +
                    ", endLine=" + endLine +
                    ", startColumn=" + startColumn +
                    ", endColumn=" + endColumn +
                    ", createTime=" + createTime +
                    ", text=" + text +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(startLine);
            parcel.writeInt(startColumn);
            parcel.writeInt(endLine);
            parcel.writeInt(endColumn);
            parcel.writeString(text.toString());
        }
    }

    /**
     * Replace action model for UndoManager
     *
     * @author Rose
     */
    public static final class ReplaceAction extends ContentAction {

        public final static Creator<ReplaceAction> CREATOR = new Creator<>() {
            @Override
            public ReplaceAction createFromParcel(Parcel parcel) {
                var o = new ReplaceAction();
                o.insert = parcel.readParcelable(ReplaceAction.class.getClassLoader());
                o.delete = parcel.readParcelable(ReplaceAction.class.getClassLoader());
                return o;
            }

            @Override
            public ReplaceAction[] newArray(int size) {
                return new ReplaceAction[size];
            }
        };
        public InsertAction insert;
        public DeleteAction delete;

        @Override
        public void undo(Content content) {
            insert.undo(content);
            delete.undo(content);
        }

        @Override
        public void redo(Content content) {
            delete.redo(content);
            insert.redo(content);
        }

        @Override
        public boolean canMerge(ContentAction action) {
            return false;
        }

        @Override
        public void merge(ContentAction action) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public String toString() {
            return "ReplaceAction{" +
                    "insert=" + insert +
                    ", delete=" + delete +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeParcelable(insert, flags);
            parcel.writeParcelable(delete, flags);
        }
    }
}
