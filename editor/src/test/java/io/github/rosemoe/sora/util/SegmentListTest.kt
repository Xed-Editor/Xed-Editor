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

package io.github.rosemoe.sora.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random


class SegmentListTest {

    @Test
    fun `test segments random modification`() {
        repeat(100) {
            val list = SegmentList<Int>()
            val std = mutableListOf<Int>()
            val random = Random(it)
            repeat(10000) {
                val cmd = random.nextInt(10)
                if (std.isEmpty() || cmd < 8) {
                    val index = random.nextInt(std.size + 1)
                    val value = random.nextInt()
                    std.add(index, value)
                    list.add(index, value)
                } else {
                    val index = random.nextInt(std.size)
                    assertThat(list.removeAt(index)).isEqualTo(std.removeAt(index))
                }
                assertThat(list.size).isEqualTo(std.size)
            }
        }
    }

    @Test
    fun `test segments random modification 2`() {
        repeat(100) {
            val list = SegmentList<Int>()
            val std = mutableListOf<Int>()
            testWith(list, std, it)
        }
    }

    @Test
    fun `test segments concurrent modification`() {
        repeat(100) { id ->
            val base = id * 3
            val list = SegmentList<Int>()
            val std = mutableListOf<Int>()

            testWith(list, std, base)

            val err = AtomicReference<Throwable?>(null)
            val copy = list.shallowCopy()
            val copyStd = mutableListOf<Int>().also { it.addAll(std) }
            val thread = Thread {
                runCatching {
                    testWith(copy, copyStd, base + 2)
                }.onFailure {
                    err.set(it)
                }
            }.also { it.start() }
            testWith(list, std, base + 1)
            thread.join()
            err.get()?.let {
                throw it
            }
        }
    }

    private fun testWith(list: SegmentList<Int>, std: MutableList<Int>, seed: Int) {
        val random = Random(seed)
        repeat(10000) {
            val cmd = random.nextInt(12)
            if (std.isEmpty() || cmd < 6) {
                val index = random.nextInt(std.size + 1)
                val value = random.nextInt()
                std.add(index, value)
                list.add(index, value)
                assertThat(list[index]).isEqualTo(std[index])
            } else if (cmd < 8) {
                val index = random.nextInt(std.size)
                val value = random.nextInt()
                assertThat(list.set(index, value)).isEqualTo(std.set(index, value))
                assertThat(list[index]).isEqualTo(std[index])
            } else if (cmd < 10) {
                var pos1 = random.nextInt(std.size + 1)
                var pos2 = random.nextInt(std.size + 1)
                if (pos1 > pos2) {
                    val tmp = pos1
                    pos1 = pos2
                    pos2 = tmp
                }
                if (pos1 != pos2) {
                    val l1 = list.subList(pos1, pos2)
                    val l2 = std.subList(pos1, pos2)
                    for (i in 0 until l2.size) {
                        assertThat(l1[i]).isEqualTo(l2[i])
                    }
                    l1.clear()
                    l2.clear()
                }
            } else {
                val index = random.nextInt(std.size)
                assertThat(list.removeAt(index)).isEqualTo(std.removeAt(index))
            }
            assertThat(list.size).isEqualTo(std.size)
        }
        for (i in 0 until std.size) {
            assertThat(list[i]).isEqualTo(std[i])
        }
    }

}