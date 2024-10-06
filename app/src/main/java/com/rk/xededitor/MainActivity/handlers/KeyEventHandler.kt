package com.rk.xededitor.MainActivity.handlers

import android.content.Intent
import android.view.KeyEvent
import com.rk.libcommons.Printer
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.BatchReplacement
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.editor.TabFragment
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.interfaces.KeyEventHandler

object KeyEventHandler {

  fun onAppKeyEvent(keyEvent: KeyEvent) {
    if (keyEvent.isCtrlPressed){
      when(keyEvent.keyCode){
        KeyEvent.KEYCODE_Q -> {
          rkUtils.toast("Force Exit, CTRL+Q")
          BaseActivity.activityMap.values.forEach { a -> a.get()?.finish() }
        }
      }
    }
  }

  val editorKeyEventHandler =
    KeyEventHandler { editor, event, editorKeyEvent, keybindingEvent, keyCode, isShiftPressed, isAltPressed, isCtrlPressed ->
      fun currentFragment(): TabFragment? {
        return MainActivity.activityRef.get()?.adapter?.getCurrentFragment()
      }

      if (isCtrlPressed) {
        when (event.keyCode) {
          KeyEvent.KEYCODE_S -> {
            currentFragment()?.save(false)
          }

          KeyEvent.KEYCODE_PLUS, 70 -> {
            editor?.let {
              if (it.textSizePx < 57) {
                it.textSizePx += 2
              }
            }
          }

          KeyEvent.KEYCODE_MINUS -> {
            editor?.let {
              if (it.textSizePx > 8) {
                it.textSizePx -= 2
              }
            }
          }

          KeyEvent.KEYCODE_F -> {
            MainActivity.activityRef.get()
              ?.let { MenuClickHandler.handle(it, it.menu.findItem(R.id.search)) }
          }

          KeyEvent.KEYCODE_H -> {
            MainActivity.activityRef.get()
              ?.let { it.startActivity(Intent(it, BatchReplacement::class.java)) }
          }

          KeyEvent.KEYCODE_P -> {
            MainActivity.activityRef.get()?.let {
              Printer.print(
                it, editor.text.toString()
              )
            }
          }

        }

      }

      //return false if you want to allow editor to process it also
      false
    }
}