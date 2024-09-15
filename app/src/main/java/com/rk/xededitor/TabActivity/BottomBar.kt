package com.rk.xededitor.TabActivity

import android.view.KeyEvent
import android.view.View
import android.widget.RelativeLayout
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils

object BottomBar {
  fun setupBottomBar(activity: TabActivity) {
    with(activity) {
      
      
      val isChecked = SettingsData.getBoolean(Keys.SHOW_ARROW_KEYS, false)
      val viewpager = binding.viewpager2
      val layoutParams = viewpager.layoutParams as RelativeLayout.LayoutParams
      layoutParams.bottomMargin = rkUtils.dpToPx(
        if (isChecked) {
          44f
        } else {
          0f
        }, this
      )
      viewpager.setLayoutParams(layoutParams)
      
      
      if (tabViewModel.fragmentFiles.isNotEmpty() && isChecked) {
        binding.apply {
          divider.visibility = View.VISIBLE
          mainBottomBar.visibility = View.VISIBLE
        }
      } else {
        binding.apply {
          divider.visibility = View.GONE
          mainBottomBar.visibility = View.GONE
        }
      }
      
      
      val arrows = binding.childs
      
      val tabSize = SettingsData.getString(Keys.TAB_SIZE, "4").toInt()
      val useSpaces = SettingsData.getBoolean(Keys.USE_SPACE_INTABS, true)
      
      val listener = View.OnClickListener { v ->
        if (isAdapterInitialized().not()) {
          return@OnClickListener
        }
        adapter.getCurrentFragment()?.let { fragment ->
          val cursor = fragment.editor!!.cursor
          
          when (v.id) {
            R.id.left_arrow -> {
              if (cursor.leftColumn - 1 >= 0) {
                fragment.editor?.setSelection(cursor.leftLine, cursor.leftColumn - 1)
              }
            }
            
            R.id.right_arrow -> {
              val lineNumber = cursor.leftLine
              val line = fragment.editor?.text!!.getLine(lineNumber)
              
              if (cursor.leftColumn < line.length) {
                fragment.editor?.setSelection(cursor.leftLine, cursor.leftColumn + 1)
                
              }
            }
            
            R.id.up_arrow -> {
              if (cursor.leftLine - 1 >= 0) {
                val upline = cursor.leftLine - 1
                val uplinestr = fragment.editor?.text!!.getLine(upline)
                
                val columm = if (uplinestr.length < cursor.leftColumn) {
                  uplinestr.length
                } else {
                  cursor.leftColumn
                }
                
                
                fragment.editor?.setSelection(cursor.leftLine - 1, columm)
              }
              
            }
            
            
            R.id.down_arrow -> {
              if (cursor.leftLine + 1 < fragment.editor!!.lineCount) {
                
                val dnline = cursor.leftLine + 1
                val dnlinestr = fragment.editor?.text!!.getLine(dnline)
                
                val columm = if (dnlinestr.length < cursor.leftColumn) {
                  dnlinestr.length
                } else {
                  cursor.leftColumn
                }
                
                fragment.editor?.setSelection(cursor.leftLine + 1, columm)
              }
            }
            
            R.id.tab -> {
              
              if (useSpaces) {
                val sb = StringBuilder()
                for (xi in 0 until tabSize) {
                  sb.append(" ")
                }
                fragment.editor?.insertText(sb.toString(), tabSize)
              } else {
                fragment.editor?.dispatchKeyEvent(
                  KeyEvent(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB
                  )
                )
              }
              
            }
            
            
            R.id.untab -> {
              if (cursor.leftColumn == 0) {
                return@OnClickListener
              }
              
              val text = fragment.editor?.text.toString()
              val line = cursor.leftLine
              val charNumber = cursor.leftColumn
              
              // Check if at least tabSize characters exist in the line
              if (charNumber >= tabSize) {
                
                // Get the substring of the current line from the beginning to tabSize
                val lineStart = text.lines()[line].take(tabSize)
                
                // Check if all characters in lineStart are spaces
                if (lineStart.all { it.isWhitespace() }) {
                  // Delete tabSize characters
                  fragment.editor?.deleteText()
                }
              }
            }
            
            R.id.home -> {
              fragment.editor?.setSelection(cursor.leftLine, 0)
            }
            
            R.id.end -> {
              fragment.editor?.setSelection(
                cursor.leftLine, fragment.editor?.text!!.getLine(cursor.leftLine)?.length ?: 0
              )
            }
          }
        }
      }
      
      
      //attach listener into every view of bottom bar
      for (i in 0 until arrows.childCount) {
        arrows.getChildAt(i).setOnClickListener(listener)
      }
    }
  }
}