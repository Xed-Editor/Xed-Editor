package com.rk.xededitor.terminal

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.core.app.NavUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.databinding.ActivityTerminalBinding
import com.rk.xededitor.rkUtils
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView


class Terminal : BaseActivity() {
  private lateinit var terminal: TerminalView
  private lateinit var binding: ActivityTerminalBinding
  private lateinit var session: TerminalSession
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityTerminalBinding.inflate(layoutInflater)
    setContentView(binding.root)
    terminal = binding.terminal
    terminal.setTerminalViewClient(TerminalClient(terminal, this))
    terminal.keepScreenOn = true
    terminal.setTextSize(rkUtils.dpToPx(13.5f, this))
    session = createSession()
    terminal.attachSession(session)
    terminal.setBackgroundColor(Color.BLACK)
    
    
    
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        rkUtils.toast(this@Terminal,"type 'exit' to exit from terminal")
      }
      
    })
  }
  
  
  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    terminal.onKeyDown(keyCode, event)
    return super.onKeyDown(keyCode, event)
  }
  
  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    terminal.updateSize()
    terminal.onScreenUpdated()
    return super.onKeyUp(keyCode, event)
  }
  
  private fun createSession(): TerminalSession {
    val workingDir = filesDir.absolutePath
    val shell = "/system/bin/sh"
    val args = arrayOf("")
    val env = arrayOf(
      "HOME=" + filesDir.absolutePath,
      "PUBLIC_HOME=" + getExternalFilesDir(null)?.absolutePath,
      "SHELL=$shell",
      "COLORTERM=truecolor",
      "TERM=xterm-256color"
    )
    return TerminalSession(
      shell,
      workingDir,
      args,
      env,
      TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
      TerminalSessionClient(terminal, this)
    )
  }
}