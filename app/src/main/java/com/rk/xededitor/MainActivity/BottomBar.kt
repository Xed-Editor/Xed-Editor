package com.rk.xededitor.MainActivity

import android.view.KeyEvent
import android.view.View
import android.widget.RelativeLayout
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.R
import com.rk.settings.PreferencesData
import com.rk.xededitor.rkUtils

object BottomBar {
  fun setupBottomBar(activity: MainActivity) {
    with(activity) {
      
      
      val isChecked = PreferencesData.getBoolean(PreferencesKeys.SHOW_ARROW_KEYS, false)
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
      
      val tabSize = PreferencesData.getString(PreferencesKeys.TAB_SIZE, "4").toInt()
      val useSpaces = PreferencesData.getBoolean(PreferencesKeys.USE_SPACE_INTABS, true)
      
      val listener = View.OnClickListener { v ->
        if (isAdapterInitialized().not()) {
          return@OnClickListener
        }
        adapter.getCurrentFragment()?.let { fragment ->
          val cursor = fragment.editor!!.cursor
          
          when (v.id) {
            R.id.left_arrow -> {
              fragment.editor?.dispatchKeyEvent(
                KeyEvent(
                  KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT
                )
              )
            }
            
            R.id.right_arrow -> {
              fragment.editor?.dispatchKeyEvent(
                KeyEvent(
                  KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
                )
              )
            }
            
            R.id.up_arrow -> {

              fragment.editor?.dispatchKeyEvent(
                KeyEvent(
                  KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP
                )
              )
              
            }
            
            
            R.id.down_arrow -> {
              fragment.editor?.dispatchKeyEvent(
                KeyEvent(
                  KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN
                )
              )
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