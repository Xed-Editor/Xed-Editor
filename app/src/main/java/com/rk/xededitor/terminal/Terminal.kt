package com.rk.xededitor.terminal

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.treeview2.TreeView
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityTerminalBinding
import com.rk.xededitor.rkUtils
import com.rk.xededitor.terminal.virtualkeys.VirtualKeysConstants
import com.rk.xededitor.terminal.virtualkeys.VirtualKeysInfo
import com.rk.xededitor.terminal.virtualkeys.VirtualKeysListener
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView


class Terminal : BaseActivity() {
  private lateinit var terminal: TerminalView
  lateinit var binding: ActivityTerminalBinding
  private lateinit var session: TerminalSession

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityTerminalBinding.inflate(layoutInflater)
    setContentView(binding.getRoot())
    terminal = TerminalView(this, null)
    terminal.setTerminalViewClient(TerminalClient(terminal, this))
    terminal.keepScreenOn = true
    terminal.setTextSize(rkUtils.dpToPx(13.5f, this))
    session = createSession()
    terminal.attachSession(session)
    terminal.setBackgroundColor(Color.BLACK)
    terminal.requestFocus()
    val params = LinearLayout.LayoutParams(-1, 0)
    params.weight = 1f
    binding.root.addView(terminal, 0, params)
    binding.extraKeys.virtualKeysViewClient = VirtualKeysListener(session)
    binding.extraKeys.reload(
      VirtualKeysInfo(
        VIRTUAL_KEYS,
        "",
        VirtualKeysConstants.CONTROL_CHARS_ALIASES
      )
    )

    window.statusBarColor = ContextCompat.getColor(this, R.color.dark)
    window.decorView.systemUiVisibility = 0
    window.navigationBarColor = ContextCompat.getColor(this, R.color.dark)

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        //do nothing
      }
    })


  }

  override fun onDestroy() {
    session.finishIfRunning()
    super.onDestroy()
  }

  private val VIRTUAL_KEYS =
    ("[" +
        "\n  [" +
        "\n    \"ESC\"," +
        "\n    {" +
        "\n      \"key\": \"/\"," +
        "\n      \"popup\": \"\\\\\"" +
        "\n    }," +
        "\n    {" +
        "\n      \"key\": \"-\"," +
        "\n      \"popup\": \"|\"" +
        "\n    }," +
        "\n    \"HOME\"," +
        "\n    \"UP\"," +
        "\n    \"END\"," +
        "\n    \"PGUP\"" +
        "\n  ]," +
        "\n  [" +
        "\n    \"TAB\"," +
        "\n    \"CTRL\"," +
        "\n    \"ALT\"," +
        "\n    \"LEFT\"," +
        "\n    \"DOWN\"," +
        "\n    \"RIGHT\"," +
        "\n    \"PGDN\"" +
        "\n  ]" +
        "\n]")

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    terminal.dispatchKeyEvent(event)
    return super.dispatchKeyEvent(event)
  }


  private fun createSession(): TerminalSession {
    val workingDir = SettingsData.getString(Keys.LAST_OPENED_PATH,filesDir.absolutePath)
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