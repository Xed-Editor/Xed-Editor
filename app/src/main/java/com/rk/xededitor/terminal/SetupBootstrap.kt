package com.rk.xededitor.terminal

import android.content.Context
import android.os.Build
import com.jaredrummler.ktsh.Shell
import com.rk.libcommons.LoadingPopup
import com.rk.librunner.commonUtils
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class SetupBootstrap(val terminal: Terminal, val runnable: Runnable) {

    private lateinit var loadingPopup: LoadingPopup
    fun init() {
        if (SettingsData.getBoolean(Keys.FAIL_SAFE,false) || File(terminal.filesDir.parentFile, "root/bin/proot").exists()) {
            runnable.run()
            return
        }

        loadingPopup = LoadingPopup(
            terminal, null
        ).setMessage(rkUtils.getString(R.string.wait_pkg))

        if (File(terminal.filesDir, "bootstrap.tar").exists().not()) {
            loadingPopup.show()
            downloadRootfs()
        }


    }

    fun getaarchName(): String {
        return when (getArch()) {
            AARCH.X86_64 -> {
                "x86_64"
            }

            AARCH.X86 -> {
                "x86"
            }

            AARCH.AARCH64 -> {
                "arm64"
            }

            AARCH.ARMV7A -> {
                "arm32"
            }

            AARCH.ARMHF -> {
                "armhf32"
            }

            AARCH.NONE -> {

                throw RuntimeException(rkUtils.getString(R.string.unsupported_aarch))
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun downloadRootfs() {
        val archName = getaarchName()


        val url =
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/$archName.tar"


        GlobalScope.launch(Dispatchers.IO) {

            if (terminal.cacheDir.exists().not()) {
                terminal.cacheDir.mkdirs()
            }

            val complete = Runnable {
                GlobalScope.launch(Dispatchers.Default) {


                    val rootfsDir = File(terminal.filesDir.parentFile, "rootfs")
                    if (rootfsDir.exists().not()) {
                        rootfsDir.mkdirs()
                    } else {
                        rootfsDir.deleteRecursively()
                    }

                    val proot = File(terminal.filesDir.parentFile, "proot")
                    if (proot.exists()) {
                        proot.delete()
                    }

                    val bootstrap = File(terminal.cacheDir.absolutePath, "bootstrap.tar")

                    exctractAssets(terminal,"proot.sh","${terminal.filesDir.parentFile!!.absolutePath}/proot.sh")

                    Shell("sh").apply {
                        run("tar -xvf ${bootstrap.absolutePath} -C ${bootstrap.parentFile!!.absolutePath}")


                        val rootfs = File(
                            File(bootstrap.parentFile!!.absolutePath, getaarchName()),
                            "rootfs.tar.gz"
                        )

                        val targetRootfs = File(terminal.filesDir.parentFile, "rootfs").apply {
                            if (exists().not()) {
                                mkdirs()
                            }
                        }
                        run("tar -xvf ${rootfs.absolutePath} -C $targetRootfs")


                        val proot = File(
                            File(bootstrap.parentFile!!.absolutePath, getaarchName()),
                            "proot.tar.gz"
                        )


                        run("tar -xvf ${proot.absolutePath} -C ${bootstrap.parentFile!!.absolutePath}")
                        run("mv ${bootstrap.parentFile!!.absolutePath}/root ${terminal.filesDir.parentFile!!.absolutePath}")
                        run("chmod +x ${terminal.filesDir.parentFile!!.absolutePath}/root/bin/proot")
                        run("rm -rf ${terminal.cacheDir.absolutePath}/*")
                        // run("echo \"${terminal.filesDir.parentFile!!.absolutePath}/root/bin/proot -S ${terminal.filesDir.parentFile!!.absolutePath}/rootfs\" > ${terminal.filesDir.parentFile!!.absolutePath}/proot.sh")

                        //fix internet
                        run("echo \"nameserver 8.8.8.8\" > ${terminal.filesDir.parentFile!!.absolutePath}/rootfs/etc/resolv.conf")
                        run("echo \"nameserver 8.8.4.4\" >> ${terminal.filesDir.parentFile!!.absolutePath}/rootfs/etc/resolv.conf")
                        shutdown()

                    }

                    exctractAssets(terminal,"init.sh","${terminal.filesDir.parentFile!!.absolutePath}/rootfs/init.sh")

                    commonUtils.exctractAssets(terminal){}

                    withContext(Dispatchers.Main) {
                        loadingPopup.hide()
                        runnable.run()
                    }


                }

            }

            val failure = Runnable {
                GlobalScope.launch(Dispatchers.Main) {
                    rkUtils.toast(rkUtils.getString(R.string.pkg_download_failed))
                    loadingPopup.hide()
                    terminal.finish()
                }
            }

            rkUtils.downloadFile(url, terminal.cacheDir.absolutePath, "bootstrap.tar", complete, failure)

        }


    }


    private enum class AARCH {
        ARMHF, ARMV7A, AARCH64, X86, X86_64, NONE
    }

    private fun getArch(): AARCH {
        val supportedAbis = Build.SUPPORTED_ABIS

        //app maybe running in a emulator with multi abi support so x86_64 is the best choice
        //from most to least preferred abi
        return if (supportedAbis.contains("x86_64")) {
            AARCH.X86_64
        } else if (supportedAbis.contains("x86")) {
            AARCH.X86
        } else if (supportedAbis.contains("arm64-v8a")) {
            AARCH.AARCH64
        } else if (supportedAbis.contains("armeabi-v7a")) {
            if (isHardFloat()) {
                AARCH.ARMHF
            } else {
                AARCH.ARMV7A
            }
        } else {
            AARCH.NONE
        }
    }


    private fun isHardFloat(): Boolean {
        val result: Shell.Command.Result
        Shell("sh").apply {
            result = run("cat /proc/cpuinfo\n")
            shutdown()
        }
        val sb = StringBuilder()
        result.stdout.forEach { line ->
            sb.append(line)
        }
        sb.toString().apply {
            if (contains("vfp") || contains("vfpv3")) {
                return true
            }
        }
        return false
    }




    fun exctractAssets(context: Context, assetFileName: String, outputFilePath: String) {
        val assetManager = context.assets
        val outputFile = File(outputFilePath)

        try {
            // Open the asset file as an InputStream
            assetManager.open(assetFileName).use { inputStream ->
                // Create an output file and its parent directories if they don't exist
                outputFile.parentFile?.mkdirs()

                // Write the input stream to the output file
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            println("${rkUtils.getString(R.string.copy_failed)}: ${e.message}")
        }
    }

}