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

package io.github.rosemoe.sora.text

import android.util.Log
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.random.nextInt

class ContentTest {

    @Test
    fun testBasicInsertions() {
        val content = Content()
        content.insert(0, 0, "Test\r\ntext")
        assertEquals("Test\r\ntext", content.toString())
        assertEquals(2, content.lineCount)

        assertEquals(5, content.getCharIndex(0, 5))
        val pos = content.indexer.getCharPosition(6)
        assertEquals(0, pos.column.toLong())
        assertEquals(1, pos.line.toLong())
        assertEquals(6, content.getCharIndex(1, 0))

        content.insert(0, 5, "v")
        assertEquals("Testv\r\ntext", content.toString())
    }

    @Test
    fun testImmutableContentIndexerQueries() {
        val content = Content("POM_SCM_URL=https://github.com/Rosemoe/sora-editor/tree/master\r\n" +
                "POM_SCM_CONNECTION=scm:git:github.com/Rosemoe/sora-editor.git\r\n" +
                "POM_SCM_DEV_CONNECTION=scm:git:ssh://github.com/Rosemoe/sora-editor.git")
        val indexer = content.indexer as CachedIndexer
        val expected = CharPosition().also {
            it.index = 63
            it.line = 0
            it.column = 63
        }
        val dest = CharPosition()

        val anchor = CharPosition().toBOF()
        indexer.findLiCoForward(anchor, 0, 63, dest)
        assertEquals(expected, dest)

        fun CharPosition.toEOF() {
            index = content.length
            line = content.lineCount - 1
            column = content.getColumnCount(line)
        }

        anchor.toEOF()
        indexer.findLiCoBackward(anchor, 0, 63, dest)
        assertEquals(expected, dest)

        anchor.toBOF()
        indexer.findIndexForward(anchor, 63, dest)
        assertEquals(expected, dest)

        anchor.toEOF()
        indexer.findIndexBackward(anchor, 63, dest)
        assertEquals(expected, dest)

        anchor.toBOF()
        assertThrows(IllegalArgumentException::class.java) {
            indexer.findIndexBackward(anchor, 63, dest)
        }

        anchor.toEOF()
        assertThrows(IllegalArgumentException::class.java) {
            indexer.findIndexForward(anchor, 63, dest)
        }
    }

    @Test
    fun testBasicRandomInsertions() {
        val content = Content()
        val shadow = StringBuilder()
        val seed = System.currentTimeMillis()
        val random = Random(seed)
        val charset = "asdfghjklqwertyuiopzxcvbnm0123456789\n\n\n"

        fun generateRandomInsertionText() : CharSequence {
            val sb = StringBuilder()
            for (i in 1..50) {
                sb.append(charset.random(random))
            }
            return sb
        }

        Log.v(this.javaClass.simpleName, "testBasicRandomInsertions: Random object is initialized with seed $seed")

        for (i in 0 until 1000) {
            val text = generateRandomInsertionText()
            val offset = random.nextInt(0..shadow.length)
            shadow.insert(offset, text)
            val pos = content.indexer.getCharPosition(offset)
            content.insert(pos.line, pos.column, text)
            val lineCount = shadow.lines().size
            assertEquals("line count is invalid", lineCount, content.lineCount)
            assertEquals("text content is invalid", shadow.toString(), content.toString())
        }
    }

}