package com.rk.xededitor.terminal

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.blankj.utilcode.util.KeyboardUtils.showSoftInput
import com.blankj.utilcode.util.SizeUtils
import com.jaredrummler.ktsh.Shell
import com.rk.libcommons.After
import com.rk.xededitor.Assets
import com.rk.xededitor.BaseActivity
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
import java.io.FileOutputStream
import java.io.IOException

class Terminal : BaseActivity() {
    private var fontSize = SizeUtils.dp2px(14f)
    lateinit var terminal: TerminalView
    private lateinit var binding: ActivityTerminalBinding
    private lateinit var session: TerminalSession
    private val terminalBackend:TerminalBackEnd = TerminalBackEnd(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)

        SetupBootstrap(this){
            setupTerminalView()
            setContentView(binding.root)
            var lastBackPressedTime: Long = 0
            val doubleBackPressTimeInterval: Long = 2000
            onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true){
                override fun handleOnBackPressed() {
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastBackPressedTime < doubleBackPressTimeInterval) {
                        terminal.mTermSession.finishIfRunning()
                        finish()
                    } else {
                        lastBackPressedTime = currentTime
                        Toast.makeText(this@Terminal, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            setupVirtualKeys()
        }.init()






    }

    override fun onDestroy() {
        terminal.mTermSession.finishIfRunning()
        super.onDestroy()
    }


    private fun setupVirtualKeys(){
        binding.extraKeys.virtualKeysViewClient = VirtualKeysListener(terminal.mTermSession)
        binding.extraKeys.reload(
            VirtualKeysInfo(
                VIRTUAL_KEYS,
                "",
                VirtualKeysConstants.CONTROL_CHARS_ALIASES
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
        terminal.keepScreenOn = true
        terminal.setTextSize(fontSize)
        val params = LinearLayout.LayoutParams(-1, 0)
        params.weight = 1f
        binding.root.addView(terminal, 0, params)

    }


//    fun exctractAssets(context: Context, assetFileName: String, outputFilePath: String) {
//        val assetManager = context.assets
//        val outputFile = File(outputFilePath)
//
//        try {
//            // Open the asset file as an InputStream
//            assetManager.open(assetFileName).use { inputStream ->
//                // Create an output file and its parent directories if they don't exist
//                outputFile.parentFile?.mkdirs()
//
//                // Write the input stream to the output file
//                FileOutputStream(outputFile).use { outputStream ->
//                    val buffer = ByteArray(1024)
//                    var length: Int
//                    while (inputStream.read(buffer).also { length = it } > 0) {
//                        outputStream.write(buffer, 0, length)
//                    }
//                }
//            }
//            println("File copied successfully to $outputFilePath")
//        } catch (e: IOException) {
//            e.printStackTrace()
//            println("Failed to copy file: ${e.message}")
//        }
//    }




    private fun createSession(): TerminalSession {
        val tmpDir = File(filesDir.parentFile,"tmp")

        if (tmpDir.exists()){
            tmpDir.deleteRecursively()
            tmpDir.mkdirs()
        }else{
            tmpDir.mkdirs()
        }

        val workingDir = SettingsData.getString(Keys.LAST_OPENED_PATH,filesDir.absolutePath)
        //val shell = File(filesDir.parentFile,"root/bin/proot").absolutePath
        val shell = "/system/bin/sh"
        val rootfs = File(filesDir.parentFile,"rootfs").absolutePath
        val args = arrayOf("-c", File(filesDir.parentFile!!,"proot.sh").absolutePath)
        val env = arrayOf(
            "PROOT_TMP_DIR=${tmpDir.absolutePath}",
            "HOME=" + filesDir.absolutePath,
            "PUBLIC_HOME=" + getExternalFilesDir(null)?.absolutePath,
            "COLORTERM=truecolor",
            "TERM=xterm-256color"
        )

        return TerminalSession(
            shell,
            workingDir,
            args,
            env,
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            terminalBackend,
        ).apply {
//            After(1000){
//                runOnUiThread {
//                    write("${File(filesDir.parentFile,"root/bin/proot").absolutePath} -S $rootfs")
//                }
//            }
        }
    }
}

const val VIRTUAL_KEYS =
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
