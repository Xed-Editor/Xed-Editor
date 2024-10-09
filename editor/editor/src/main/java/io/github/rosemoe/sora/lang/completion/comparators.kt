/*******************************************************************************
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
 ******************************************************************************/
@file:JvmName("Comparators")


package io.github.rosemoe.sora.lang.completion

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.CharCode

private fun CharSequence?.asString(): String {
    return if (this == null) " " else if (this is String) this else this.toString()
}

fun defaultComparator(a: CompletionItem, b: CompletionItem): Int {
    // check score
    val p1Score = (a.extra as? SortedCompletionItem)?.score?.score ?: 0
    val p2Score = (b.extra as? SortedCompletionItem)?.score?.score ?: 0

    // if score biggest, it better similar to input text
    if (p1Score < p2Score) {
        return 1;
    } else if (p1Score > p2Score) {
        return -1;
    }

    var p1 = a.sortText.asString()
    var p2 = b.sortText.asString()

    // check with 'sortText'

    if (p1 < p2) {
        return -1;
    } else if (p1 > p2) {
        return 1;
    }

    p1 = a.label.asString()
    p2 = b.label.asString()

    // check with 'label'
    if (p1 < p2) {
        return -1;
    } else if (p1 > p2) {
        return 1;
    }

    // check with 'kind'
    // if kind biggest, it better important
    val kind = (b.kind?.value ?: 0) - (a.kind?.value ?: 0)

    return kind
}

fun snippetUpComparator(a: CompletionItem, b: CompletionItem): Int {
    if (a.kind != b.kind) {
        if (a.kind == CompletionItemKind.Snippet) {
            return 1;
        } else if (b.kind == CompletionItemKind.Snippet) {
            return -1;
        }
    }
    return defaultComparator(a, b);
}


fun getCompletionItemComparator(
    source: ContentReference,
    cursorPosition: CharPosition,
    completionItemList: Collection<CompletionItem>
): Comparator<CompletionItem> {

    source.validateAccess()

    val sourceLine = source.reference.getLine(cursorPosition.line)

    var word = ""
    var wordLow = ""

    // picks a score function based on the number of
    // items that we have to score/filter and based on the
    // user-configuration

    val scoreFn = FuzzyScorer { pattern,
                                lowPattern,
                                patternPos,
                                wordText,
                                lowWord,
                                wordPos,
                                options ->
        if (sourceLine.length > 2000) {
            fuzzyScore(pattern, lowPattern, patternPos, wordText, lowWord, wordPos, options)
        } else {
            fuzzyScoreGracefulAggressive(
                pattern,
                lowPattern,
                patternPos,
                wordText,
                lowWord,
                wordPos,
                options
            )
        }

    }

    for (originItem in completionItemList) {

        source.validateAccess()

        val overwriteBefore = originItem.prefixLength
        val wordLen = overwriteBefore
        if (word.length != wordLen) {
            word = if (wordLen == 0) "" else sourceLine.substring(
                sourceLine.length - wordLen
            )
            wordLow = word.lowercase()
        }


        val item = SortedCompletionItem(originItem, FuzzyScore.default)

        // when there is nothing to score against, don't
        // event try to do. Use a const rank and rely on
        // the fallback-sort using the initial sort order.
        // use a score of `-100` because that is out of the
        // bound of values `fuzzyScore` will return
        if (wordLen == 0) {
            // when there is nothing to score against, don't
            // event try to do. Use a const rank and rely on
            // the fallback-sort using the initial sort order.
            // use a score of `-100` because that is out of the
            // bound of values `fuzzyScore` will return
            item.score = FuzzyScore.default
        } else {
            // skip word characters that are whitespace until
            // we have hit the replace range (overwriteBefore)
            var wordPos = 0;
            while (wordPos < overwriteBefore) {
                val ch = word[wordPos].code
                if (ch == CharCode.Space || ch == CharCode.Tab) {
                    wordPos += 1;
                } else {
                    break;
                }
            }

            if (wordPos >= wordLen) {
                // the wordPos at which scoring starts is the whole word
                // and therefore the same rules as not having a word apply
                item.score = FuzzyScore.default;
            } else if (originItem.sortText?.isNotEmpty() == true) {
                // when there is a `filterText` it must match the `word`.
                // if it matches we check with the label to compute highlights
                // and if that doesn't yield a result we have no highlights,
                // despite having the match
                // by default match `word` against the `label`
                val match = scoreFn.calculateScore(
                    word,
                    wordLow,
                    wordPos,
                    originItem.sortText.asString(),
                    originItem.sortText.asString().lowercase(),
                    0,
                    FuzzyScoreOptions.default
                ) ?: continue; // NO match

                // compareIgnoreCase(item.completion.filterText, item.textLabel) === 0
                if (originItem.sortText === originItem.label) {
                    // filterText and label are actually the same -> use good highlights
                    item.score = match;
                } else {
                    // re-run the scorer on the label in the hope of a result BUT use the rank
                    // of the filterText-match
                    val labelMatch = scoreFn.calculateScore(
                        word,
                        wordLow,
                        wordPos,
                        originItem.label.asString(),
                        originItem.label.asString().lowercase(),
                        0,
                        FuzzyScoreOptions.default
                    ) ?: continue; // NO match
                    item.score = labelMatch
                    labelMatch.matches[0] = match.matches[0]
                }

            } else {
                // by default match `word` against the `label`
                val match = scoreFn.calculateScore(
                    word,
                    wordLow,
                    wordPos,
                    originItem.label.asString(),
                    originItem.label.asString().lowercase(),
                    0,
                    FuzzyScoreOptions.default
                ) ?: continue; // NO match
                item.score = match;
            }

            originItem.extra = item

        }
    }

    return Comparator { o1, o2 ->
        snippetUpComparator(o1, o2)
    }
}

data class SortedCompletionItem(
    val completionItem: CompletionItem,
    var score: FuzzyScore
)
