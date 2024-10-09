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
@file:JvmName("Filters")

package io.github.rosemoe.sora.lang.completion

import io.github.rosemoe.sora.util.CharCode
import io.github.rosemoe.sora.util.MyCharacter

// Migrating from vscode
// https://github.com/microsoft/vscode/blob/main/src/vs/base/common/filters.ts


private var maxLen = 32
private val minWordMatchPosArray = IntArray(2 * maxLen)
private val maxWordMatchPosArray = IntArray(2 * maxLen)

val diag = Array(maxLen) { IntArray(maxLen) } // the length of a contiguous diagonal match
val table = Array(maxLen) { IntArray(maxLen) }
val arrows = Array(maxLen) { IntArray(maxLen) }


object Arrow {
    val Diag = 1
    val Left = 2
    val LeftLeft = 3
}

@JvmOverloads
fun isPatternInWord(
    patternLow: String,
    patternPos: Int,
    patternLen: Int,
    wordLow: String,
    wordPos: Int,
    wordLen: Int,
    fillMinWordPosArr: Boolean = false
): Boolean {
    var patternPosMut = patternPos
    var wordPosMut = wordPos
    while (patternPosMut < patternLen && wordPosMut < wordLen) {
        if (patternLow[patternPosMut] == wordLow[wordPosMut]) {
            if (fillMinWordPosArr) {
                // Remember the min word position for each pattern position
                minWordMatchPosArray[patternPosMut] = wordPosMut
            }
            patternPosMut += 1
        }
        wordPosMut += 1
    }
    return patternPosMut == patternLen // pattern must be exhausted
}


internal fun fillInMaxWordMatchPos(
    patternLen: Int,
    wordLen: Int,
    patternStart: Int,
    wordStart: Int,
    patternLow: String,
    wordLow: String
) {
    var patternPos = patternLen - 1
    var wordPos = wordLen - 1
    while (patternPos >= patternStart && wordPos >= wordStart) {
        if (patternLow[patternPos] == wordLow[wordPos]) {
            maxWordMatchPosArray[patternPos] = wordPos
            patternPos--
        }
        wordPos--
    }
}

fun isUpperCaseAtPos(pos: Int, word: String, wordLow: String): Boolean {
    return word[pos] != wordLow[pos]
}


fun isSeparatorAtPos(value: String, index: Int): Boolean {
    if (index < 0 || index >= value.length) {
        return false
    }
    return when (val code = value.codePointAt(index)) {
        CharCode.Underline,
        CharCode.Dash,
        CharCode.Period,
        CharCode.Space,
        CharCode.Slash,
        CharCode.Backslash,
        CharCode.SingleQuote,
        CharCode.DoubleQuote,
        CharCode.Colon,
        CharCode.DollarSign,
        CharCode.LessThan,
        CharCode.GreaterThan,
        CharCode.OpenParen,
        CharCode.CloseParen,
        CharCode.OpenSquareBracket,
        CharCode.CloseSquareBracket,
        CharCode.OpenCurlyBrace,
        CharCode.CloseCurlyBrace -> true

        else -> MyCharacter.couldBeEmoji(code)

    }
}

fun isWhitespaceAtPos(value: String, index: Int): Boolean {
    if (index < 0 || index >= value.length) {
        return false
    }

    return when (value[index].code) {
        CharCode.Space,
        CharCode.Tab -> true

        else -> false

    }
}

/**
 * An array representing a fuzzy match.
 *
 * 0. the score
 * 1. the offset at which matching started
 * 2. `<match_pos_N>`
 * 3. `<match_pos_1>`
 * 4. `<match_pos_0>` etc
 */
class FuzzyScore(
    var score: Int,
    val wordStart: Int,
    val matches: MutableList<Int> = mutableListOf()
) {

    companion object {
        /**
         * No matches and value `-100`
         */
        @JvmStatic
        val default: FuzzyScore = FuzzyScore(-100, 0)

        @JvmStatic
        fun isDefault(score: FuzzyScore?): Boolean {
            return score?.score == -100 && score.wordStart == 0
        }
    }

}

data class FuzzyScoreOptions(
    val firstMatchCanBeWeak: Boolean,
    val boostFullMatch: Boolean,
) {

    companion object {
        @JvmStatic
        val default = FuzzyScoreOptions(boostFullMatch = true, firstMatchCanBeWeak = false)
    }

}

fun interface FuzzyScorer {
    fun calculateScore(
        pattern: String,
        lowPattern: String,
        patternPos: Int,
        word: String,
        lowWord: String,
        wordPos: Int,
        options: FuzzyScoreOptions?
    ): FuzzyScore?
}

fun anyScore(
    pattern: String,
    lowPattern: String,
    patternPos: Int,
    word: String,
    lowWord: String,
    wordPos: Int,
): FuzzyScore {
    val max = 13.coerceAtMost(pattern.length)
    var patternPosMut = patternPos
    while (patternPosMut < max) {
        val result = fuzzyScore(
            pattern, lowPattern, patternPosMut, word, lowWord, wordPos,
            FuzzyScoreOptions(firstMatchCanBeWeak = false, boostFullMatch = true)
        )
        if (result != null) {
            return result
        }
        patternPosMut++
    }

    return FuzzyScore(0, wordPos)
}


@JvmOverloads
fun fuzzyScore(
    pattern: String,
    patternLow: String,
    patternStart: Int,
    word: String,
    wordLow: String,
    wordStart: Int,
    options: FuzzyScoreOptions? = FuzzyScoreOptions.default
): FuzzyScore? {

    val patternLen = if (pattern.length > maxLen) maxLen else pattern.length
    val wordLen = if (word.length > maxLen - 1) maxLen - 1 else word.length

    if (patternStart >= patternLen || wordStart >= wordLen || (patternLen - patternStart) > (wordLen - wordStart)) {
        return null
    }

    // Run a simple check if the characters of pattern occur
    // (in order) at all in word. If that isn't the case we
    // stop because no match will be possible
    if (!isPatternInWord(patternLow, patternStart, patternLen, wordLow, wordStart, wordLen, true)) {
        return null
    }

    // Find the max matching word position for each pattern position
    // NOTE: the min matching word position was filled in above, in the `isPatternInWord` call
    fillInMaxWordMatchPos(patternLen, wordLen, patternStart, wordStart, patternLow, wordLow)

    var row = 1
    var column = 1
    var patternPos = patternStart
    var wordPos: Int

    val hasStrongFirstMatch = booleanArrayOf(false)

    // There will be a match, fill in tables
    while (patternPos < patternLen) {

        // Reduce search space to possible matching word positions and to possible access from next row
        val minWordMatchPos = minWordMatchPosArray[patternPos]
        val maxWordMatchPos = maxWordMatchPosArray[patternPos]
        val nextMaxWordMatchPos =
            if (patternPos + 1 < patternLen) maxWordMatchPosArray[patternPos + 1] else wordLen

        column = minWordMatchPos - wordStart + 1
        wordPos = minWordMatchPos

        while (wordPos < nextMaxWordMatchPos) {

            var score = Int.MIN_VALUE
            var canComeDiag = false

            if (wordPos <= maxWordMatchPos) {
                score = doScore(
                    pattern, patternLow, patternPos, patternStart,
                    word, wordLow, wordPos, wordLen, wordStart,
                    diag[row - 1][column - 1] == 0,
                    hasStrongFirstMatch
                )
            }

            var diagScore = 0
            if (score != Int.MAX_VALUE) {
                canComeDiag = true
                diagScore = score + table[row - 1][column - 1]
            }

            val canComeLeft = wordPos > minWordMatchPos
            val leftScore =
                if (canComeLeft) table[row][column - 1] + (if (diag[row][column - 1] > 0) -5 else 0) else 0 // penalty for a gap start

            val canComeLeftLeft = wordPos > minWordMatchPos + 1 && diag[row][column - 1] > 0
            val leftLeftScore =
                if (canComeLeftLeft) table[row][column - 2] + (if (diag[row][column - 2] > 0) -5 else 0) else 0 // penalty for a gap start

            if (canComeLeftLeft && (!canComeLeft || leftLeftScore >= leftScore) && (!canComeDiag || leftLeftScore >= diagScore)) {
                // always prefer choosing left left to jump over a diagonal because that means a match is earlier in the word
                table[row][column] = leftLeftScore
                arrows[row][column] = Arrow.LeftLeft
                diag[row][column] = 0
            } else if (canComeLeft && (!canComeDiag || leftScore >= diagScore)) {
                // always prefer choosing left since that means a match is earlier in the word
                table[row][column] = leftScore
                arrows[row][column] = Arrow.Left
                diag[row][column] = 0
            } else if (canComeDiag) {
                table[row][column] = diagScore
                arrows[row][column] = Arrow.Diag
                diag[row][column] = diag[row - 1][column - 1] + 1
            } else {
                error("not possible")
            }
            column++
            wordPos++
        }
        row++
        patternPos++
    }



    if (!hasStrongFirstMatch[0] && options?.firstMatchCanBeWeak == false) {
        return null
    }

    row--
    column--

    val result = FuzzyScore(table[row][column], wordStart)

    var backwardsDiagLength = 0
    var maxMatchColumn = 0

    while (row >= 1) {
        // Find the column where we go diagonally up
        var diagColumn = column
        do {
            val arrow = arrows[row][diagColumn]
            if (arrow == Arrow.LeftLeft) {
                diagColumn -= 2
            } else if (arrow == Arrow.Left) {
                diagColumn -= 1
            } else {
                // found the diagonal
                break
            }
        } while (diagColumn >= 1)

        // Overturn the "forwards" decision if keeping the "backwards" diagonal would give a better match
        if (
            backwardsDiagLength > 1 // only if we would have a contiguous match of 3 characters
            && patternLow[patternStart + row - 1] == wordLow[wordStart + column - 1] // only if we can do a contiguous match diagonally
            && !isUpperCaseAtPos(
                diagColumn + wordStart - 1,
                word,
                wordLow
            ) // only if the forwards chose diagonal is not an uppercase
            && backwardsDiagLength + 1 > diag[row][diagColumn] // only if our contiguous match would be longer than the "forwards" contiguous match
        ) {
            diagColumn = column
        }

        if (diagColumn == column) {
            // this is a contiguous match
            backwardsDiagLength++
        } else {
            backwardsDiagLength = 1
        }

        if (maxMatchColumn == 0) {
            // remember the last matched column
            maxMatchColumn = diagColumn
        }

        row--
        column = diagColumn - 1
        result.matches.add(column)
    }

    if (wordLen == patternLen && options?.boostFullMatch == true) {
        // the word matches the pattern with all characters!
        // giving the score a total match boost (to come up ahead other words)
        result.score += 2
    }

    // Add 1 penalty for each skipped character in the word
    val skippedCharsCount = maxMatchColumn - patternLen
    result.score -= skippedCharsCount

    return result
}


internal fun doScore(
    pattern: String, patternLow: String, patternPos: Int, patternStart: Int,
    word: String, wordLow: String, wordPos: Int, wordLen: Int, wordStart: Int,
    newMatchStart: Boolean,
    outFirstMatchStrong: BooleanArray
): Int {
    if (patternLow[patternPos] != wordLow[wordPos]) {
        return Int.MIN_VALUE
    }

    var score = 1
    var isGapLocation = false
    if (wordPos == patternPos - patternStart) {
        // common prefix: `foobar <-> foobaz`
        //                            ^^^^^
        score = if (pattern[patternPos] == word[wordPos]) 7 else 5

    } else if (isUpperCaseAtPos(wordPos, word, wordLow) && (wordPos == 0 || !isUpperCaseAtPos(
            wordPos - 1,
            word,
            wordLow
        ))
    ) {
        // hitting upper-case: `foo <-> forOthers`
        //                              ^^ ^
        score = if (pattern[patternPos] == word[wordPos]) 7 else 5
        isGapLocation = true

    } else if (isSeparatorAtPos(wordLow, wordPos) && (wordPos == 0 || !isSeparatorAtPos(
            wordLow,
            wordPos - 1
        ))
    ) {
        // hitting a separator: `. <-> foo.bar`
        //                                ^
        score = 5
    } else if (isSeparatorAtPos(wordLow, wordPos - 1) || isWhitespaceAtPos(wordLow, wordPos - 1)) {
        // post separator: `foo <-> bar_foo`
        //                              ^^^
        score = 5
        isGapLocation = true
    }

    if (score > 1 && patternPos == patternStart) {
        outFirstMatchStrong[0] = true
    }

    if (!isGapLocation) {
        isGapLocation = isUpperCaseAtPos(wordPos, word, wordLow) || isSeparatorAtPos(
            wordLow,
            wordPos - 1
        ) || isWhitespaceAtPos(wordLow, wordPos - 1)
    }

    //
    if (patternPos == patternStart) { // first character in pattern
        if (wordPos > wordStart) {
            // the first pattern character would match a word character that is not at the word start
            // so introduce a penalty to account for the gap preceding this match
            score -= if (isGapLocation) 3 else 5
        }
    } else {
        if (newMatchStart) {
            // this would be the beginning of a new match (i.e. there would be a gap before this location)
            score += if (isGapLocation) 2 else 0
        } else {
            // this is part of a contiguous match, so give it a slight bonus, but do so only if it would not be a preferred gap location
            score += if (isGapLocation) 0 else 1
        }
    }

    if (wordPos + 1 == wordLen) {
        // we always penalize gaps, but this gives unfair advantages to a match that would match the last character in the word
        // so pretend there is a gap after the last character in the word to normalize things
        score -= if (isGapLocation) 3 else 5
    }

    return score
}


fun fuzzyScoreGracefulAggressive(
    pattern: String,
    lowPattern: String,
    patternPos: Int,
    word: String,
    lowWord: String,
    wordPos: Int,
    options: FuzzyScoreOptions?
): FuzzyScore? {
    return fuzzyScoreWithPermutations(
        pattern,
        lowPattern,
        patternPos,
        word,
        lowWord,
        wordPos,
        true,
        options
    )
}

fun fuzzyScoreGraceful(
    pattern: String,
    lowPattern: String,
    patternPos: Int,
    word: String,
    lowWord: String,
    wordPos: Int,
    options: FuzzyScoreOptions?
): FuzzyScore? {
    return fuzzyScoreWithPermutations(
        pattern,
        lowPattern,
        patternPos,
        word,
        lowWord,
        wordPos,
        false,
        options
    )
}

internal fun fuzzyScoreWithPermutations(
        pattern: String,
        lowPattern: String,
        patternPos: Int,
        word: String,
        lowWord: String,
        wordPos: Int,
        aggressive: Boolean,
        options: FuzzyScoreOptions?
    ): FuzzyScore? {
    var top = fuzzyScore(
        pattern,
        lowPattern,
        patternPos,
        word,
        lowWord,
        wordPos,
        options ?: FuzzyScoreOptions.default
    )

    if (top != null && !aggressive) {
        // when using the original pattern yield a result we`
        // return it unless we are aggressive and try to find
        // a better alignment, e.g. `cno` -> `^co^ns^ole` or `^c^o^nsole`.
        return top
    }

    if (pattern.length >= 3) {
        // When the pattern is long enough then try a few (max 7)
        // permutations of the pattern to find a better match. The
        // permutations only swap neighbouring characters, e.g
        // `cnoso` becomes `conso`, `cnsoo`, `cnoos`.
        val tries = 7.coerceAtMost(pattern.length - 1)

        var movingPatternPos = patternPos + 1

        while (movingPatternPos < tries) {
            val newPattern = nextTypoPermutation(pattern, movingPatternPos)
            if (newPattern != null) {
                val candidate = fuzzyScore(
                    newPattern,
                    newPattern.lowercase(),
                    patternPos,
                    word,
                    lowWord,
                    wordPos,
                    options ?: FuzzyScoreOptions.default
                )
                if (candidate != null) {
                    candidate.score -= 3 // permutation penalty
                    if (top == null || candidate.score > top.score) {
                        top = candidate
                    }
                }
            }
            movingPatternPos++
        }
    }

    return top
}

internal fun nextTypoPermutation(pattern: String, patternPos: Int): String? {

    if (patternPos + 1 >= pattern.length) {
        return null
    }

    val swap1 = pattern[patternPos]
    val swap2 = pattern[patternPos + 1]

    if (swap1 == swap2) {
        return null
    }

    return pattern.substring(0, patternPos) + swap2 + swap1 + pattern.substring(patternPos + 2)
}

