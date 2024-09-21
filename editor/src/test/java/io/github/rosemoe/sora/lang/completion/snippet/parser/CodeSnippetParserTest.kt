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
package io.github.rosemoe.sora.lang.completion.snippet.parser

import com.google.common.truth.Truth.assertThat
import io.github.rosemoe.sora.lang.completion.snippet.ConditionalFormat
import io.github.rosemoe.sora.lang.completion.snippet.PlaceholderItem
import io.github.rosemoe.sora.lang.completion.snippet.PlainPlaceholderElement
import io.github.rosemoe.sora.lang.completion.snippet.PlainTextItem
import io.github.rosemoe.sora.lang.completion.snippet.VariableItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * @author Akash Yadav
 */
@RunWith(RobolectricTestRunner::class)
class CodeSnippetParserTest {

  @Test
  fun `test variable in placeholder without braces`() {

    CodeSnippetParser.parse("line: \${1:\$TM_CURRENT_LINE}").apply {
      assertThat(this).isNotNull()
      this.items!!.apply {
        assertThat(this).hasSize(2)

        this[0].apply {
          assertThat(this).isInstanceOf(PlainTextItem::class.java)
          assertThat((this as PlainTextItem).text).isEqualTo("line: ")
        }

        this[1].apply {
          assertThat(this).isInstanceOf(PlaceholderItem::class.java)
          assertThat((this as PlaceholderItem).definition.id).isEqualTo(1)

          assertThat(this.definition.text).isNull()

          this.definition.elements.apply {
            assertThat(this).hasSize(1)

            this[0].apply {
              assertThat(this).isInstanceOf(VariableItem::class.java)
              assertThat((this as VariableItem).name).isEqualTo("TM_CURRENT_LINE")
            }
          }
        }
      }
    }
  }

  @Test
  fun `test variable in placeholder`() {

    CodeSnippetParser.parse("line: \${1:\${TM_CURRENT_LINE}}").apply {
      assertThat(this).isNotNull()
      this.items!!.apply {
        assertThat(this).hasSize(2)

        this[0].apply {
          assertThat(this).isInstanceOf(PlainTextItem::class.java)
          assertThat((this as PlainTextItem).text).isEqualTo("line: ")
        }

        this[1].apply {
          assertThat(this).isInstanceOf(PlaceholderItem::class.java)
          assertThat((this as PlaceholderItem).definition.id).isEqualTo(1)

          assertThat(this.definition.text).isNull()

          this.definition.elements.apply {
            assertThat(this).hasSize(1)

            this[0].apply {
              assertThat(this).isInstanceOf(VariableItem::class.java)
              assertThat((this as VariableItem).name).isEqualTo("TM_CURRENT_LINE")
            }
          }
        }
      }
    }
  }

  @Test
  fun `test complex variable in placeholder`() {

    CodeSnippetParser.parse("filename: \${1:\${TM_FILENAME/(.*)/\${1:/upcase}/}}").apply {
      assertThat(this).isNotNull()
      this.items!!.apply {
        assertThat(this).hasSize(2)

        this[0].apply {
          assertThat(this).isInstanceOf(PlainTextItem::class.java)
          assertThat((this as PlainTextItem).text).isEqualTo("filename: ")
        }

        this[1].apply {
          assertThat(this).isInstanceOf(PlaceholderItem::class.java)
          assertThat((this as PlaceholderItem).definition.id).isEqualTo(1)

          assertThat(this.definition.text).isNull()

          this.definition.elements.apply {
            assertThat(this).hasSize(1)

            this[0].apply {
              assertThat(this).isInstanceOf(VariableItem::class.java)
              assertThat((this as VariableItem).name).isEqualTo("TM_FILENAME")
              assertThat(this.transform).isNotNull()
              assertThat(this.transform!!.format).isNotNull()

              this.transform!!.format!!.apply {
                assertThat(this).hasSize(1)

                this[0].apply {
                  assertThat(this).isInstanceOf(ConditionalFormat::class.java)
                  assertThat((this as ConditionalFormat).shorthand).isEqualTo("upcase")
                }
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun `test plain text before variable in placeholder`() {
    CodeSnippetParser.parse("\${1:line=\${TM_CURRENT_LINE}}").apply {
      assertThat(this).isNotNull()
      this.items.apply {
        assertThat(this).hasSize(1)

        this[0].apply {
          assertThat(this).isNotNull()
          assertThat(this).isInstanceOf(PlaceholderItem::class.java)
          assertThat((this as PlaceholderItem).definition.elements).isNotEmpty()

          this.definition.elements.apply {
            assertThat(this).hasSize(2)

            this[0].apply {
              assertThat(this).isInstanceOf(PlainPlaceholderElement::class.java)
              assertThat((this as PlainPlaceholderElement).text).isEqualTo("line=")
            }

            this[1].apply {
              assertThat(this).isInstanceOf(VariableItem::class.java)
              assertThat((this as VariableItem).name).isEqualTo("TM_CURRENT_LINE")
            }
          }
        }
      }
    }
  }

  @Test
  fun `test plain text after variable in placeholder`() {
    CodeSnippetParser.parse("\${1:\${TM_CURRENT_LINE} is current line}").apply {
      assertThat(this).isNotNull()
      this.items.apply {
        assertThat(this).hasSize(1)

        this[0].apply {
          assertThat(this).isNotNull()
          assertThat(this).isInstanceOf(PlaceholderItem::class.java)
          assertThat((this as PlaceholderItem).definition.elements).isNotEmpty()

          this.definition.elements.apply {
            assertThat(this).hasSize(2)

            this[0].apply {
              assertThat(this).isInstanceOf(VariableItem::class.java)
              assertThat((this as VariableItem).name).isEqualTo("TM_CURRENT_LINE")
            }

            this[1].apply {
              assertThat(this).isInstanceOf(PlainPlaceholderElement::class.java)
              assertThat((this as PlainPlaceholderElement).text).isEqualTo(" is current line")
            }

          }
        }
      }
    }
  }
}