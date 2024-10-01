package com.rk.xededitor.terminal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.blankj.utilcode.util.SizeUtils
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.ProjectManager
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
import java.io.File


class Terminal : BaseActivity() {
  lateinit var terminal: TerminalView
  lateinit var binding: ActivityTerminalBinding
  private lateinit var session: TerminalSession
  private val terminalBackend: TerminalBackEnd = TerminalBackEnd(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityTerminalBinding.inflate(layoutInflater)

    SetupBootstrap(this) {
      setupTerminalView()
      setContentView(binding.root)
      setupVirtualKeys()

      var lastBackPressedTime: Long = 0
      val doubleBackPressTimeInterval: Long = 2000
      onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          val currentTime = System.currentTimeMillis()

          if (currentTime - lastBackPressedTime < doubleBackPressTimeInterval) {
            terminal.mTermSession.finishIfRunning()
            finish()
          } else {
            lastBackPressedTime = currentTime
            Toast.makeText(
              this@Terminal, rkUtils.getString(R.string.press_again_exit), Toast.LENGTH_SHORT
            ).show()
          }
        }
      })

    }.init()


  }

  override fun onDestroy() {
    terminal.mTermSession.finishIfRunning()
    super.onDestroy()
  }


  private fun setupVirtualKeys() {
    binding.extraKeys.virtualKeysViewClient = VirtualKeysListener(terminal.mTermSession)
    binding.extraKeys.reload(
      VirtualKeysInfo(
        VIRTUAL_KEYS, "", VirtualKeysConstants.CONTROL_CHARS_ALIASES
      )
    )
  }


  private fun setupTerminalView() {
    terminal = TerminalView(this, null)
    terminalBackend.setTerminal(terminal)
    terminal.setTerminalViewClient(terminalBackend)
    session = createSession()
    terminal.attachSession(session)
    terminal.setBackgroundColor(Color.BLACK)
    terminal.setTextSize(
      SizeUtils.dp2px(
        SettingsData.getString(Keys.TERMINAL_TEXT_SIZE, "14").toFloat()
      )
    )
    terminal.keepScreenOn = true
    val params = LinearLayout.LayoutParams(-1, 0)
    params.weight = 1f
    binding.root.addView(terminal, 0, params)

  }


  companion object {
    @JvmStatic
    fun runCommand(
      //run in alpine or not
      alpine: Boolean,
      //shell or binary to run
      shell: String,
      //arguments passed to shell or binary
      args: Array<String> = arrayOf(),
      //working directory leave empty for default
      workingDir: String = "",
      //environment variables with key value pair eg HOME=/sdcard,TMP=/tmp
      environmentVars: Array<String>? = arrayOf(),
      //should override default environment variables or not
      overrideEnv: Boolean = false,
      //context to launch terminal activity
      context: Context
    ) {
      context.startActivity(Intent(
        context, Terminal::class.java
      ).also {
        it.putExtra("run_cmd", true)
        it.putExtra("shell", shell)
        it.putExtra("args", args)
        it.putExtra("cwd", workingDir)
        it.putExtra("env", environmentVars)
        it.putExtra("overrideEnv", overrideEnv)
        it.putExtra("alpine", alpine)
      })
    }
  }


  private fun createSession(): TerminalSession {
    val workingDir = if (intent.hasExtra("cwd")) {
      intent.getStringExtra("cwd")
    } else if (MainActivity.activityRef.get() != null && ProjectManager.projects.isNotEmpty()) {
      ProjectManager.currentProject.get(MainActivity.activityRef.get()!!).absolutePath
    } else {
      filesDir.absolutePath
    }

    val tmpDir = File(getTempDir(), "terminal")

    if (tmpDir.exists()) {
      tmpDir.deleteRecursively()
      tmpDir.mkdirs()
    } else {
      tmpDir.mkdirs()
    }

    val env = arrayOf(
      "PROOT_TMP_DIR=${tmpDir.absolutePath}",
      "HOME=" + filesDir.absolutePath,
      "PUBLIC_HOME=" + getExternalFilesDir(null)?.absolutePath,
      "COLORTERM=truecolor",
      "TERM=xterm-256color"
    )


    if (intent.hasExtra("run_cmd")) {
      var shell = "/system/bin/sh"
      val args: Array<String>

      var cwd = intent.getStringExtra("cwd")

      if (cwd!!.isEmpty()) {
        cwd = workingDir
      }

      val env1 = if (intent.getBooleanExtra("overrideEnv", false)) {
        intent.getStringArrayExtra("env")
      } else {
        with(mutableListOf<String>()) {
          addAll(env)
          addAll(intent.getStringArrayExtra("env")!!.toList())
          toTypedArray()
        }
      }

      val alpine = intent.getBooleanExtra("alpine", true)


      if (alpine) {
        args = mutableListOf("-c", File(filesDir.parentFile!!, "proot.sh").absolutePath,intent.getStringExtra("shell")!!).also {
          it.addAll(intent.getStringArrayExtra("args")!!.toList())
        }.toTypedArray()
      } else {
        shell = intent.getStringExtra("shell").toString()
        args = intent.getStringArrayExtra("args")!!
      }

      return TerminalSession(
        shell,
        cwd,
        args,
        env1,
        TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
        terminalBackend,
      )


    }


    val shell = "/system/bin/sh"
    val args = if (SettingsData.getBoolean(Keys.FAIL_SAFE, false)) {
      arrayOf("")
    } else {
      arrayOf("-c", File(filesDir.parentFile!!, "proot.sh").absolutePath)
    }


    return TerminalSession(
      shell,
      workingDir,
      args,
      env,
      TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
      terminalBackend,
    )
  }
}

const val VIRTUAL_KEYS =
  ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"/\"," + "\n      \"popup\": \"\\\\\"" + "\n    }," + "\n    {" + "\n      \"key\": \"-\"," + "\n      \"popup\": \"|\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")
