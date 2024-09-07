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
package io.github.rosemoe.sora.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.time.DurationUnit
import kotlin.time.measureTime


class ContentShallowCopyTest {

    companion object {
        val DEFAULT_CHARSET = "abcdefghijklmnopqrstuvwxyz1234567890 \n\n\n"

        val TEXT = """
            implementation(libs.androidx.constraintlayout)
            implementation(libs.gms.instantapps)
        
            // Desugar
            coreLibraryDesugaring(libs.desugar)
        
            // androidx & material
            implementation(libs.material)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.lifecycle.runtime)""".trimIndent().trim().replace("\r\n", "\n")
    }

    @Test
    fun `test shallow copy performance`() {
        val text = Content(TEXT.repeat(100), false)
        val iterationCount = 1000
        val timeDeep = measureTime {
            repeat(iterationCount) {
                text.copyText(false)
            }
        }
        val timeShallow = measureTime {
            repeat(iterationCount) {
                text.copyTextShallow()
            }
        }
        println(
            "Content Shallow Copy Perf Test Result:\n" +
                    "Deep Copy Time = $timeDeep, Shallow Copy Time = $timeShallow," +
                    " Ratio = ${
                        timeDeep.toLong(DurationUnit.NANOSECONDS) / timeShallow.toLong(
                            DurationUnit.NANOSECONDS
                        ).toDouble()
                    }"
        )
        assertThat(timeShallow).isAtMost(timeDeep)
    }

    @Test
    fun `test shallow copy instance`() {
        val text = Content(TEXT, false)
        assertThat(text.toString()).isEqualTo(TEXT)
        for (i in 0 until text.lineCount) {
            assertThat(text.getLine(i).isMutable()).isTrue()
        }

        val copy = text.copyTextShallow()
        assertThat(copy.lineCount).isEqualTo(text.lineCount)
        assertThat(copy.toString()).isEqualTo(text.toString())
        for (i in 0 until text.lineCount) {
            assertThat(copy.getLine(i).isMutable()).isFalse()
            assertThat(copy.getLine(i)).isSameInstanceAs(text.getLine(i))
        }

        copy.release()
        assertThat(copy.lineCount).isEqualTo(0)
        for (i in 0 until text.lineCount) {
            assertThat(text.getLine(i).isMutable()).isTrue()
        }
    }

    @Test
    fun `test shallow copy modification`() {
        val text = Content(TEXT, false)
        val insertion = "//Test Content\n"
        val copy = text.copyTextShallow()
        copy.insert(0, 0, insertion)
        assertThat(copy.lineCount).isEqualTo(text.lineCount + 1)
        for (i in 0 until copy.lineCount) {
            assertThat(copy.getLine(i).isMutable()).apply {
                if (i <= 1) {
                    isTrue()
                } else {
                    isFalse()
                }
            }
        }
        assertThat(text.getLine(0).isMutable()).isTrue()
        assertThat(text.toString()).isEqualTo(TEXT)
        assertThat(copy.toString()).isEqualTo(insertion + TEXT)
        copy.delete(0, 0, 2, 0)
        assertThat(copy.getLine(0).isMutable()).isTrue()
        for (i in 0..1) {
            assertThat(text.getLine(i).isMutable()).isTrue()
        }
    }

    @Test
    fun `test shallow copy modification 2`() {
        val text = Content(TEXT, false)
        val copy = text.copyTextShallow()
        copy.delete(0, 5)
        assertThat(text.toString()).isEqualTo(TEXT)
        assertThat(copy.toString()).isEqualTo(TEXT.substring(5))
        assertThat(text.getLine(0).isMutable()).isTrue()
        assertThat(copy.getLine(0).isMutable()).isTrue()
    }

    @Test
    fun `test shallow copy random modification`() {
        testRandomModification(DEFAULT_CHARSET)
    }

    @Test
    fun `test shallow copy random modification 2`() {
        testRandomModification(DEFAULT_CHARSET.replace("\n", ""))
    }

    private fun testRandomModification(charset: String) {
        val text = Content(TEXT, false)
        val deepCopy = text.copyText(false)
        val shallowCopy = text.copyTextShallow()
        val sb = text.toStringBuilder()
        assertThat(deepCopy.length).isEqualTo(text.length)
        assertThat(shallowCopy.length).isEqualTo(text.length)
        assertThat(sb.length).isEqualTo(text.length)
        val edits = prepareEdits(initialLen = text.length)
        edits.forEachIndexed { i, edit ->
            edit.let {
                it.apply(sb)
                it.apply(deepCopy)
                it.apply(shallowCopy)
            }
            runCatching {
                assertThat(text.length).isEqualTo(TEXT.length)
                assertThat(deepCopy.length).isEqualTo(sb.length)
                assertThat(shallowCopy.length).isEqualTo(sb.length)
            }.onFailure {
                println("Expected Text:\n$sb")
                println("Got Text:\n${deepCopy}")
                println("Edit: $edit")
                throw it
            }
        }
        assertThat(text.toString()).isEqualTo(TEXT)
        assertThat(deepCopy.toString()).isEqualTo(sb.toString())
        assertThat(shallowCopy.toString()).isEqualTo(sb.toString())
    }

    @Test
    fun `test shallow copy concurrent modification`() {
        val text = Content(TEXT, false)
        val copy = text.copyTextShallow()

        val editsText =
            prepareEdits(text.length, editCount = 100000, insertionLen = 100, seed = 12345)
        val editsCopy =
            prepareEdits(copy.length, editCount = 100000, insertionLen = 100, seed = 123456)

        val sbText = text.toStringBuilder()
        val sbCopy = copy.toStringBuilder()

        val err = AtomicReference<Throwable?>(null)
        val thread = Thread {
            runCatching {
                testOnEdits(sbCopy, copy, editsCopy)
            }.onFailure {
                err.set(it)
            }
        }
        thread.start()
        testOnEdits(sbText, text, editsText)
        thread.join()
        err.get()?.let { throw it }
    }

    private fun testOnEdits(sb: StringBuilder, content: Content, edits: List<Edit>) {
        edits.forEach {
            it.apply(sb)
            it.apply(content)
            assertThat(content.length).isEqualTo(sb.length)
        }
        assertThat(content.toString()).isEqualTo(sb.toString())
    }

    private fun prepareEdits(
        initialLen: Int = 0,
        editCount: Int = 1000,
        insertionLen: Int = 50,
        insertionProbability: Float = 0.8f,
        seed: Int = 10000,
        charset: String = DEFAULT_CHARSET
    ): List<Edit> {
        val edits = mutableListOf<Edit>()
        var len = initialLen
        val r = Random(seed)
        for (i in 0 until editCount) {
            val index = r.nextInt(len + 1)
            if (r.nextFloat() < insertionProbability || index == len || len == 0) {
                edits.add(
                    Insert(
                        r.nextInt(len + 1),
                        String(CharArray(insertionLen) { charset.random(r) })
                    )
                )
                len += insertionLen
            } else {
                val end = r.nextInt(index, len + 1)
                edits.add(Delete(index, end))
                len -= end - index
            }
        }
        return edits
    }

    abstract class Edit(val index: Int) {
        abstract fun apply(content: Content)

        abstract fun apply(sb: StringBuilder)
    }

    class Insert(index: Int, val text: CharSequence) : Edit(index) {
        override fun apply(content: Content) {
            val pos = content.indexer.getCharPosition(index)
            content.insert(pos.line, pos.column, text)
        }

        override fun apply(sb: StringBuilder) {
            sb.insert(index, text)
        }

        override fun toString(): String {
            return "Insert {index = $index, text = '$text'}"
        }
    }

    class Delete(start: Int, val end: Int) : Edit(start) {
        override fun apply(content: Content) {
            content.delete(index, end)
        }

        override fun apply(sb: StringBuilder) {
            sb.delete(index, end)
        }

        override fun toString(): String {
            return "Delete {start = $index, end = $end}"
        }
    }

}