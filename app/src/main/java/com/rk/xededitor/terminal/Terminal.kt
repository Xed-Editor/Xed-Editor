package com.rk.xededitor.terminal

import android.os.Bundle
import android.view.KeyEvent
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.databinding.ActivityTerminalBinding
import com.rk.xededitor.rkUtils
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView


class Terminal : BaseActivity() {
  private lateinit var terminal: TerminalView
  private lateinit var binding:ActivityTerminalBinding
  private lateinit var session: TerminalSession
  
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityTerminalBinding.inflate(layoutInflater)
    setContentView(binding.root)
    terminal = binding.terminal
    terminal.setTerminalViewClient(TerminalClient(terminal,this))
    terminal.keepScreenOn = true
    terminal.setTextSize(rkUtils.dpToPx(16f,this))
    session = createSession()
    terminal.attachSession(session)
  }
  
  
  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    terminal.onKeyDown(keyCode,event)
    return super.onKeyDown(keyCode, event)
  }
  
  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    // Handle key release if needed
    return super.onKeyUp(keyCode, event)
  }
  
  
  private fun createSession(): TerminalSession {
    val workingDir = "/"
    val shell = "/system/bin/sh"
    val args = arrayOf("")
    val env = arrayOf("")
    
    session = TerminalSession(shell,workingDir,args,env,
      TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
      TerminalSessionClient(terminal,this))
    return session
  }
}