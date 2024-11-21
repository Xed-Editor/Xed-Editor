package com.rk.xededitor.terminal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.blankj.utilcode.util.SizeUtils
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivityTerminalBinding
import com.rk.xededitor.rkUtils
import com.rk.xededitor.terminal.MkSession.createSession
import com.rk.xededitor.terminal.virtualkeys.VirtualKeysConstants
import com.rk.xededitor.terminal.virtualkeys.VirtualKeysInfo
import com.rk.xededitor.terminal.virtualkeys.VirtualKeysListener
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.io.File

class Terminal : BaseActivity() {
    var terminal: TerminalView? = null
    var binding: ActivityTerminalBinding? = null
    private lateinit var session: TerminalSession
    val terminalBackend: TerminalBackEnd = TerminalBackEnd(this)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        
        SetupAlpine(this){
            setupTerminalView()
            setContentView(binding!!.root)
            setupVirtualKeys()
            
            var lastBackPressedTime: Long = 0
            val doubleBackPressTimeInterval: Long = 2000
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        val currentTime = System.currentTimeMillis()
                        
                        if (currentTime - lastBackPressedTime < doubleBackPressTimeInterval) {
                            terminal?.mTermSession?.finishIfRunning()
                            finish()
                        } else {
                            lastBackPressedTime = currentTime
                            Toast.makeText(
                                this@Terminal,
                                rkUtils.getString(strings.press_again_exit),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            )
        }.init()
    }
    
    override fun onDestroy() {
        terminal?.mTermSession?.finishIfRunning()
        super.onDestroy()
    }
    
    private fun setupVirtualKeys() {
        binding!!.extraKeys.virtualKeysViewClient = terminal?.mTermSession?.let { VirtualKeysListener(it) }
        binding!!.extraKeys.reload(
            VirtualKeysInfo(VIRTUAL_KEYS, "", VirtualKeysConstants.CONTROL_CHARS_ALIASES)
        )
    }
    
    private fun setupTerminalView() {
        terminal = TerminalView(this, null)
        terminalBackend.setTerminal(terminal!!)
        terminal!!.setTerminalViewClient(terminalBackend)
        session = createSession(this)
        terminal!!.attachSession(session)
        terminal!!.setBackgroundColor(Color.BLACK)
        terminal!!.setTextSize(
            SizeUtils.dp2px(
                PreferencesData.getString(PreferencesKeys.TERMINAL_TEXT_SIZE, "14").toFloat()
            )
        )
        
        terminal!!.keepScreenOn = true
        val params = LinearLayout.LayoutParams(-1, 0)
        params.weight = 1f
        binding!!.root.addView(terminal, 0, params)
        terminal!!.requestFocus()
        terminal!!.setFocusableInTouchMode(true)
        
        
        val customFont = File(Environment.getExternalStorageDirectory(), "karbon/terminal_font.ttf")
        if (customFont.exists() and customFont.isFile) {
            terminal!!.setTypeface(Typeface.createFromFile(customFont))
        }
    }
    
    companion object {
        @JvmStatic
        @JvmOverloads
        fun runCommand(
            // run in alpine or not
            alpine: Boolean,
            // shell or binary to run
            shell: String,
            // arguments passed to shell or binary
            args: Array<String> = arrayOf(),
            // working directory leave empty for default
            workingDir: String = "",
            // environment variables with key value pair eg HOME=/sdcard,TMP=/tmp
            environmentVars: Array<String>? = arrayOf(),
            // should override default environment variables or not
            overrideEnv: Boolean = false,
            // context to launch terminal activity
            context: Context,
        ) {
            context.startActivity(Intent(context, Terminal::class.java).also {
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
    
    
}

const val VIRTUAL_KEYS =
    ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"/\"," + "\n      \"popup\": \"\\\\\"" + "\n    }," + "\n    {" + "\n      \"key\": \"-\"," + "\n      \"popup\": \"|\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")
