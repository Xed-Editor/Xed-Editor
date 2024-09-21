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
package io.github.rosemoe.sora.langs.textmate;


import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import org.eclipse.tm4e.languageconfiguration.internal.model.CompleteEnterAction;
import org.eclipse.tm4e.languageconfiguration.internal.model.EnterAction;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;
import org.eclipse.tm4e.languageconfiguration.internal.supports.IndentRulesSupport;
import org.eclipse.tm4e.languageconfiguration.internal.supports.OnEnterSupport;
import org.eclipse.tm4e.languageconfiguration.internal.utils.TextUtils;

import java.util.Arrays;

import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;


public class TextMateNewlineHandler implements NewlineHandler {

    private OnEnterSupport enterSupport = null;

    private IndentRulesSupport indentRulesSupport = null;

    private final TextMateLanguage language;

    private CompleteEnterAction enterAction;

    private boolean isEnabled = true;

    //private static final Pattern precedingValidPattern = Pattern.compile("^\\s+$");

    private Pair<String, String> indentForEnter;

    private LanguageConfiguration languageConfiguration;

    public TextMateNewlineHandler(TextMateLanguage language) {
        this.language = language;
        var languageConfiguration = language.languageConfiguration;

        this.languageConfiguration = languageConfiguration;

        if (languageConfiguration == null) {
            return;
        }

        var enterRules = languageConfiguration.getOnEnterRules();
        var brackets = languageConfiguration.getBrackets();

        var indentationsRules = languageConfiguration.getIndentationRules();

        if (enterRules != null) {
            enterSupport = new OnEnterSupport(brackets, enterRules);
        }

        if (indentationsRules != null) {
            indentRulesSupport = new IndentRulesSupport(indentationsRules);
        }
    }

    @Override
    public boolean matchesRequirement(@NonNull Content text, @NonNull CharPosition position, @Nullable Styles style) {

        if (!isEnabled) {
            return false;
        }

        enterAction = getEnterAction(text, position);

        indentForEnter = null;

        if (enterAction == null) {
            indentForEnter = getIndentForEnter(text, position);
        }

        return enterAction != null || indentForEnter != null;
    }

    @NonNull
    @Override
    public NewlineHandleResult handleNewline(@NonNull Content text, @NonNull CharPosition position, @Nullable Styles style, int tabSize) {

        // https://github.com/microsoft/vscode/blob/bf63ea1932dd253745f38a4cbe26bb9be01801b1/src/vs/editor/common/cursor/cursorTypeOperations.ts#L309

        var delim = "\n"; /*command.text;*/


        if (indentForEnter != null) {

            var normalIndent = normalizeIndentation(indentForEnter.second);
            var typeText = delim + normalIndent;

            //var caretOffset = normalIndent.length() ;
            return new NewlineHandleResult(typeText, 0);
        }

        switch (enterAction.indentAction) {
            case None:
            case Indent: {
                // Nothing special
                final var increasedIndent = normalizeIndentation(enterAction.indentation + enterAction.appendText);
                final var typeText = delim + increasedIndent;

                // var caretOffset = typeText.length();
                // offset value is not needed because the editor ignores the position of invisible characters when moving the cursor

                return new NewlineHandleResult(typeText, 0);
            }// Indent once
            case IndentOutdent: {
                // Ultra special
                final var normalIndent = normalizeIndentation(enterAction.indentation);
                final var increasedIndent = normalizeIndentation(enterAction.indentation + enterAction.appendText);
                final var typeText = delim + increasedIndent + delim + normalIndent;

                var caretOffset = normalIndent.length() + 1;
                return new NewlineHandleResult(typeText, caretOffset);
            }
            case Outdent:
                final var indentation = TextUtils.getIndentationFromWhitespace(enterAction.indentation, language.getTabSize(), language.useTab());
                final var outdentedText = outdentString(normalizeIndentation(indentation + enterAction.appendText));

                var caretOffset = outdentedText.length() + 1;
                return new NewlineHandleResult(outdentedText, caretOffset);

        }

        return new NewlineHandleResult("", 0);

    }


    protected Pair<String, String> getIndentForEnter(Content text, CharPosition position) {
        // https://github.com/microsoft/vscode/blob/bf63ea1932dd253745f38a4cbe26bb9be01801b1/src/vs/editor/common/languages/autoIndent.ts#L278

        var currentLineText = text.getLineString(position.line);

        var beforeEnterText = currentLineText.substring(0, position.column);

        var afterEnterText = currentLineText.substring(position.column);

        if (indentRulesSupport == null) {
            return null;
        }

        var beforeEnterIndent = TextUtils.getLeadingWhitespace(beforeEnterText, 0, beforeEnterText.length());

        var afterEnterAction = getInheritIndentForLine(new WrapperContentImp(text, position.line, beforeEnterText), true, position.line + 1);


        if (afterEnterAction == null) {
            return new Pair<>(beforeEnterIndent, beforeEnterIndent);
        }

        var afterEnterIndent = afterEnterAction.indentation;
        var indent = "";

        if (language.useTab()) {
            indent = " ".repeat(language.getTabSize());
        } else {
            indent = "\t";
        }


        //var firstNonScapeIndex = TextUtils.firstNonWhitespaceIndex(beforeEnterIndent);
        if (afterEnterAction.action == EnterAction.IndentAction.Indent) {

            // 	afterEnterIndent = indentConverter.shiftIndent(afterEnterIndent)

            //var invisibleColumn = TextUtils.invisibleColumnFromColumn(text, position.line, position.column, tabSpaces);

            afterEnterIndent = beforeEnterIndent + indent;
        }

        if (indentRulesSupport.shouldDecrease(afterEnterText)) {
            // afterEnterIndent = indentConverter.unshiftIndent(afterEnterIndent);

            afterEnterIndent = beforeEnterIndent.substring(0, Math.max(Math.max(0, beforeEnterIndent.length() - 1), beforeEnterIndent.length() - indent.length() /*- 1*/));
        }

        return new Pair<>(beforeEnterIndent, afterEnterIndent);

    }


    /**
     * Get inherited indentation from above lines.
     * 1. Find the nearest preceding line which doesn't match unIndentedLinePattern.
     * 2. If this line matches indentNextLinePattern or increaseIndentPattern, it means that the indent level of `lineNumber` should be 1 greater than this line.
     * 3. If this line doesn't match any indent rules
     * a. check whether the line above it matches indentNextLinePattern
     * b. If not, the indent level of this line is the result
     * c. If so, it means the indent of this line is *temporary*, go upward utill we find a line whose indent is not temporary (the same workflow a -> b -> c).
     * 4. Otherwise, we fail to get an inherited indent from aboves. Return null and we should not touch the indent of `lineNumber`
     * <p>
     * This function only return the inherited indent based on above lines, it doesn't check whether current line should decrease or not.
     */
    @Nullable
    private InheritIndentResult getInheritIndentForLine(WrapperContent wrapperContent,
                                                        boolean honorIntentialIndent, int line) {
        // https://github.com/microsoft/vscode/blob/bf63ea1932dd253745f38a4cbe26bb9be01801b1/src/vs/editor/common/languages/autoIndent.ts#L73

        if (line <= 0) {
            return new InheritIndentResult("", null);
        }

        var precedingUnIgnoredLine = getPrecedingValidLine(wrapperContent, line);

        if (precedingUnIgnoredLine <= -1) {
            return null;
        } /*else if (precedingUnIgnoredLine < 0) {
            return new InheritIndentResult("", null);
        }*/

        var precedingUnIgnoredLineContent = wrapperContent.getLineContent(precedingUnIgnoredLine);
        if (indentRulesSupport.shouldIncrease(precedingUnIgnoredLineContent) || indentRulesSupport.shouldIndentNextLine(precedingUnIgnoredLineContent)) {
            return new InheritIndentResult(TextUtils.getLeadingWhitespace(precedingUnIgnoredLineContent, 0, precedingUnIgnoredLineContent.length()), EnterAction.IndentAction.Indent, precedingUnIgnoredLine);
        } else if (indentRulesSupport.shouldDecrease(precedingUnIgnoredLineContent)) {
            return new InheritIndentResult(TextUtils.getLeadingWhitespace(precedingUnIgnoredLineContent, 0, precedingUnIgnoredLineContent.length()), null, precedingUnIgnoredLine);
        } else {
            // precedingUnIgnoredLine can not be ignored.
            // it doesn't increase indent of following lines
            // it doesn't increase just next line
            // so current line is not affect by precedingUnIgnoredLine
            // and then we should get a correct inheritted indentation from above lines
            if (precedingUnIgnoredLine == 0) {
                return new InheritIndentResult(TextUtils.getLeadingWhitespace(wrapperContent.getLineContent(precedingUnIgnoredLine)), null, precedingUnIgnoredLine);
            }


            var previousLine = precedingUnIgnoredLine - 1;

            var previousLineIndentMetadata = indentRulesSupport.getIndentMetadata(wrapperContent.getLineContent(previousLine));

            // 	if (!(previousLineIndentMetadata & (IndentConsts.INCREASE_MASK | IndentConsts.DECREASE_MASK)) &&
            //			(previousLineIndentMetadata & IndentConsts.INDENT_NEXTLINE_MASK)) {
            if (((previousLineIndentMetadata & (IndentRulesSupport.IndentConsts.INCREASE_MASK | IndentRulesSupport.IndentConsts.DECREASE_MASK)) == 0) && (previousLineIndentMetadata & IndentRulesSupport.IndentConsts.INDENT_NEXTLINE_MASK) == 0 && previousLineIndentMetadata > 0) {

                var stopLine = 0;
                for (var i = previousLine - 1; i > 0; i--) {
                    if (indentRulesSupport.shouldIndentNextLine(wrapperContent.getLineContent((i)))) {
                        continue;
                    }
                    stopLine = i;
                    break;
                }

                return new InheritIndentResult(TextUtils.getLeadingWhitespace(wrapperContent.getLineContent(stopLine + 1)), null, stopLine + 1);
            }

            if (honorIntentialIndent) {
                return new InheritIndentResult(
                        TextUtils.getLeadingWhitespace(wrapperContent.getLineContent(precedingUnIgnoredLine)), null, precedingUnIgnoredLine);
            }


            // search from precedingUnIgnoredLine until we find one whose indent is not temporary
            for (var i = precedingUnIgnoredLine; i > 0; i--) {
                var lineContent = wrapperContent.getLineContent(i);
                if (indentRulesSupport.shouldIncrease(lineContent)) {
                    return new InheritIndentResult(TextUtils.getLeadingWhitespace(lineContent), EnterAction.IndentAction.Indent, i);
                } else if (indentRulesSupport.shouldIndentNextLine(lineContent)) {
                    var stopLine = 0;
                    for (var j = i - 1; j > 0; j--) {
                        if (indentRulesSupport.shouldIndentNextLine(wrapperContent.getLineContent(i))) {
                            continue;
                        }
                        stopLine = j;
                        break;
                    }


                    return new InheritIndentResult(TextUtils.getLeadingWhitespace(wrapperContent.getLineContent(stopLine + 1)), null, stopLine + 1);

                } else if (indentRulesSupport.shouldDecrease(lineContent)) {
                    return new InheritIndentResult(TextUtils.getLeadingWhitespace(lineContent), null, i);
                }
            }

            return new InheritIndentResult(TextUtils.getLeadingWhitespace(wrapperContent.getLineContent(1)), null, 1);
        }
    }


    /**
     * Get nearest preceding line which doesn't match unIndentPattern or contains all whitespace.
     * Result:
     * -1: run into the boundary of embedded languages
     * 0: every line above are invalid
     * else: nearest preceding line of the same language
     */
    public int getPrecedingValidLine(WrapperContent content, int lineNumber) {
        // remove embeddedLanguages support
        // const languageId = model.tokenization.getLanguageIdAtPosition(lineNumber, 0);
        if (lineNumber > 0) {
            int lastLineNumber;

            for (lastLineNumber = lineNumber - 1; lastLineNumber >= 0; lastLineNumber--) {
                var text = content.getLineContent(lastLineNumber);
                if (indentRulesSupport.shouldIgnore(text) /*|| precedingValidPattern.matcher(text).matches()*/ || text.isEmpty()) {
                    continue;
                }

                return lastLineNumber;
            }
        }

        return -1;
    }

    /**
     * @see <a href="https://github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/enterAction.ts">
     * https://github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/enterAction.ts</a>
     */
    @Nullable
    public CompleteEnterAction getEnterAction(final Content content, final CharPosition position) {
        String indentation = TextUtils.getLinePrefixingWhitespaceAtPosition(content, position);
        // let scopedLineTokens = this.getScopedLineTokens(model, range.startLineNumber, range.startColumn);
        final var onEnterSupport = this.enterSupport;


        if (onEnterSupport == null) {
            return null;
        }

        var scopedLineText = content.getLineString(position.line);


        var beforeEnterText = scopedLineText.substring(0, position.column  /*- 0*/ /*scopedLineTokens.firstCharOffset*/);


        // String afterEnterText = null;

        // selection support
        // if (range.isEmpty()) {
        var afterEnterText = scopedLineText.substring(position.column /*- scopedLineTokens.firstCharOffset*/);
        // afterEnterText = scopedLineText.substr(range.startColumn - 1 - scopedLineTokens.firstCharOffset);
        // } else {
        // const endScopedLineTokens = this.getScopedLineTokens(model,
        // range.endLineNumber, range.endColumn);
        // afterEnterText = endScopedLineTokens.getLineContent().substr(range.endColumn - 1 -
        // scopedLineTokens.firstCharOffset);
        // }

        /*
         * let lineNumber = range.startLineNumber; let oneLineAboveText = '';
         *
         * if (lineNumber > 1 && scopedLineTokens.firstCharOffset === 0) { // This is
         * not the first line and the entire line belongs to this mode let
         * oneLineAboveScopedLineTokens = this.getScopedLineTokens(model, lineNumber -
         * 1); if (oneLineAboveScopedLineTokens.languageId ===
         * scopedLineTokens.languageId) { // The line above ends with text belonging to
         * the same mode oneLineAboveText =
         * oneLineAboveScopedLineTokens.getLineContent(); } }
         */

        /*
        let previousLineText = '';
        if (range.startLineNumber > 1 && scopedLineTokens.firstCharOffset === 0) {
            // This is not the first line and the entire line belongs to this mode
		const oneLineAboveScopedLineTokens = getScopedLineTokens(model, range.startLineNumber - 1);
            if (oneLineAboveScopedLineTokens.languageId === scopedLineTokens.languageId) {
                // The line above ends with text belonging to the same mode
                previousLineText = oneLineAboveScopedLineTokens.getLineContent();
            }
        } */

        var previousLineText = "";

        if (position.line > 1 /*|| position.column == 0*/) {
            // This is not the first line and the entire line belongs to this mode
            // The line above ends with text belonging to the same mode
            previousLineText = content.getLineString(position.line - 1);
        }

        EnterAction enterResult = null;
        try {
            enterResult = onEnterSupport.onEnter(previousLineText, beforeEnterText, afterEnterText);
        } catch (final Exception e) {
            e.printStackTrace();
            // onUnexpectedError(e);
        }

        if (enterResult == null) {
            return null;
        }

        final EnterAction.IndentAction indentAction = enterResult.indentAction;
        String appendText = enterResult.appendText;
        final Integer removeText = enterResult.removeText;
        // Here we add `\t` to appendText first because enterAction is leveraging
        // appendText and removeText to change indentation.
        if (appendText == null) {
            if (indentAction == EnterAction.IndentAction.Indent
                    || indentAction == EnterAction.IndentAction.IndentOutdent) {
                appendText = "\t";
            } else {
                appendText = "";
            }
        } else if (indentAction == EnterAction.IndentAction.Indent) {
            appendText = "\t" + appendText;
        }

        if (removeText != null) {
            indentation = indentation.substring(0, indentation.length() - removeText);
        }

        return new CompleteEnterAction(indentAction, appendText, removeText, indentation);

    }


    private String outdentString(final String str) {
        if (str.startsWith("\t")) { // $NON-NLS-1$
            return str.substring(1);
        }

        if (language.useTab()) {
            final char[] chars = new char[language.getTabSize()];
            Arrays.fill(chars, ' ');
            final String spaces = new String(chars);
            if (str.startsWith(spaces)) {
                return str.substring(spaces.length());
            }
        }
        return str;
    }


    private String normalizeIndentation(final String str) {
        return TextUtils.normalizeIndentation(str, language.getTabSize(), !language.useTab());
    }


    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }


    static class InheritIndentResult {
        String indentation;
        EnterAction.IndentAction action;
        int line;

        public InheritIndentResult(String indentation, EnterAction.IndentAction action, int line) {
            this.indentation = indentation;
            this.action = action;
            this.line = line;
        }

        public InheritIndentResult(String indentation, EnterAction.IndentAction action) {
            this.indentation = indentation;
            this.action = action;
        }
    }

    /*
        const virtualModel: IVirtualModel = {
                tokenization: {
            getLineTokens: (lineNumber: number) => {
                return model.tokenization.getLineTokens(lineNumber);
            },
            getLanguageId: () => {
                return model.getLanguageId();
            },
            getLanguageIdAtPosition: (lineNumber: number, column: number) => {
                return model.getLanguageIdAtPosition(lineNumber, column);
            },
        },
        getLineContent: (lineNumber: number) => {
            if (lineNumber === range.startLineNumber) {
                return beforeEnterResult;
            } else {
                return model.getLineContent(lineNumber);
            }
        }
       };
       */
    private static class WrapperContentImp implements WrapperContent {
        private final Content content;
        private final int line;
        private final String currentLineContent;

        protected WrapperContentImp(Content content, int line, String currentLineContent) {
            this.content = content;
            this.line = line;
            this.currentLineContent = currentLineContent;
        }

        @Override
        public Content getOrigin() {
            return content;
        }

        @Override
        public String getLineContent(int line) {
            if (line == this.line) {
                return currentLineContent;
            } else {
                return content.getLineString(line);
            }
        }
    }

    private interface WrapperContent {
        Content getOrigin();

        String getLineContent(int line);
    }

}
