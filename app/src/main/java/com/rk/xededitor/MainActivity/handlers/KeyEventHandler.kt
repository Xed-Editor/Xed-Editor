package com.rk.xededitor.MainActivity.handlers

import android.content.Intent
import android.view.KeyEvent
import androidx.core.app.ActivityCompat.startActivityForResult
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.libcommons.Printer
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.file.FileAction.Companion.to_save_file
import com.rk.xededitor.MainActivity.file.REQUEST_CODE_OPEN_DIRECTORY
import com.rk.xededitor.R
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.interfaces.KeyEventHandler
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorKeyEventHandler
object KeyEventHandler {

    init {
        EditorKeyEventHandler.userKeyEventHandler =
            KeyEventHandler { isProcessedByEditor, editor, event, editorKeyEvent, keybindingEvent, keyCode, isShiftPressed, isAltPressed, isCtrlPressed ->
                if (event != null) {
                    onAppKeyEvent(event)
                }
                isProcessedByEditor
            }
    }

    private var lastCallTime = 0L
    fun onAppKeyEvent(keyEvent: KeyEvent) {

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCallTime >= 100) {
            lastCallTime = currentTime
        }else{
            return
        }

        val currentFragment = MainActivity.activityRef.get()?.adapter?.getCurrentFragment()
        val editor = if (currentFragment?.type == FragmentType.EDITOR) {
            (currentFragment.fragment as EditorFragment).editor
        } else {
            null
        }
        
        if (keyEvent.isShiftPressed) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    currentFragment?.fragment?.getFile()?.let {
                        throw RuntimeException("disabled")
                        //to_save_file = it
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        MainActivity.activityRef.get()?.let { activity ->
                            startActivityForResult(
                                activity,
                                intent,
                                REQUEST_CODE_OPEN_DIRECTORY,
                                null,
                            )
                        }
                    }
                }
            }
        }
        
        if (keyEvent.isCtrlPressed) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_W -> {
                    MainActivity.activityRef.get()?.apply {
                        adapter!!.removeFragment(tabLayout!!.selectedTabPosition,true)
                        binding!!.tabs.invalidate()
                        binding!!.tabs.requestLayout()
                        
                        // Detach and re-attach the TabLayoutMediator
                        TabLayoutMediator(binding!!.tabs, viewPager!!) { tab, position ->
                            tab.text = tabViewModel.fragmentTitles[position]
                        }.attach()
                        MenuItemHandler.update(this)
                        
                    }
                }
                
                KeyEvent.KEYCODE_K -> {
                    MainActivity.activityRef.get()?.let {
                        if (it.tabLayout!!.selectedTabPosition == 0) {
                            return
                        }
                        it.tabLayout!!.selectTab(
                            it.tabLayout!!.getTabAt(it.tabLayout!!.selectedTabPosition - 1)
                        )
                        val fragment = it.adapter!!.getCurrentFragment()?.fragment
                        if (fragment is EditorFragment) {
                            fragment.editor?.requestFocus()
                            fragment.editor?.requestFocusFromTouch()
                        }
                    }
                }
                
                KeyEvent.KEYCODE_L -> {
                    MainActivity.activityRef.get()?.let {
                        if (it.tabLayout!!.selectedTabPosition == it.tabLayout!!.tabCount - 1) {
                            return
                        }
                        it.tabLayout!!.selectTab(
                            it.tabLayout!!.getTabAt(it.tabLayout!!.selectedTabPosition + 1)
                        )
                        val fragment = it.adapter!!.getCurrentFragment()?.fragment
                        if (fragment is EditorFragment) {
                            fragment.editor?.requestFocus()
                            fragment.editor?.requestFocusFromTouch()
                        }
                    }
                }
                
                KeyEvent.KEYCODE_S -> {
                    if (currentFragment?.fragment is EditorFragment) {
                        (currentFragment.fragment as EditorFragment).save(false)
                    }
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
                    MainActivity.activityRef.get()?.let {
                        MenuClickHandler.handle(it, it.menu!!.findItem(R.id.search))
                    }
                }
                
                KeyEvent.KEYCODE_P -> {
                    MainActivity.activityRef.get()?.let {
                        Printer.print(it, editor?.text.toString())
                    }
                }
                
                KeyEvent.KEYCODE_G -> {
                    editor?.let {
                        val line = 0
                        var column = (editor.text.getLine(line).length).coerceAtMost(
                            editor.cursor.leftColumn
                        )
                        if (column < 0) {
                            column = 0
                        }
                        editor.cursor.set(line, column)
                    }
                    
                }
                
                KeyEvent.KEYCODE_B -> {
                    editor?.let {
                        val line = editor.text.lineCount - 1
                        var column = (editor.text.getLine(line).length - 1).coerceAtMost(
                            editor.cursor.leftColumn
                        )
                        if (column < 0) {
                            column = 0
                        }
                        editor.cursor.set(line, column)
                    }
                    
                }
            }
            
        }
    }
}
