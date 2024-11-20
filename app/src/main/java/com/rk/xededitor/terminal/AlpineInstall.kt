package com.rk.xededitor.terminal

import android.content.Context
import android.os.Build
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.ktsh.Shell
import com.rk.libcommons.LoadingPopup
import com.rk.resources.strings
import com.rk.runner.commonUtils
import com.rk.runner.commonUtils.exctractAssets
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.Debug
import com.rk.xededitor.rkUtils.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermission


class AlpineInstall(val terminal: Terminal) {
    
    private val tempDir = File(terminal.getTempDir(), "alpineInstall")
    private val rootfsDir = File(terminal.filesDir.parentFile, "rootfs")
    private val prootDir = File(terminal.filesDir.parentFile, "root")
    
    fun installIfNot(onDone: () -> Unit) {
        Debug("check install")
        if (PreferencesData.getBoolean(PreferencesKeys.FAIL_SAFE, false) || (prootDir.exists() && rootfsDir.exists())) {
            onDone.invoke()
            Debug("skipping")
            return
        }
        
        prootDir.mkdirs()
        rootfsDir.mkdirs()
        
        Debug("show popup")
        val loadingPopup = LoadingPopup(terminal, null).setMessage(rkUtils.getString(strings.wait_pkg))
        loadingPopup.show()
        
        terminal.lifecycleScope.launch(Dispatchers.Main) {
            debug("launch setupInternal")
            runCatching { withContext(Dispatchers.IO) { setupInternal() } }.onFailure {
                rkUtils.toast(it.message)
                tempDir.deleteRecursively()
                terminal.finish()
                loadingPopup.hide()
                debug("setup failed because ${it.message}")
                throw it
            }.onSuccess { loadingPopup.hide();debug("setup success");onDone.invoke() }
        }
        
    }
    
    @Throws(Exception::class)
    private suspend fun setupInternal() {
        debug("cleaning")
        clean()
        val supportedAbis = Build.SUPPORTED_ABIS
        
        val AlpineUrl = if (supportedAbis.contains("x86_64")) {
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/alpineX64.tar"
        } else if (supportedAbis.contains("x86")) {
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/alpineX86.tar"
        } else if (supportedAbis.contains("arm64-v8a")) {
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/alpineArm64.tar"
        } else if (supportedAbis.contains("armeabi-v7a")) {
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/alpineArm32.tar"
        } else {
            throw RuntimeException("Unsupported Architecture")
        }
        
        
        val alpineTar = if (supportedAbis.contains("x86_64")) {
            "alpineX64.tar"
        } else if (supportedAbis.contains("x86")) {
            "alpineX86.tar"
        } else if (supportedAbis.contains("arm64-v8a")) {
            "alpineArm64.tar"
        } else if (supportedAbis.contains("armeabi-v7a")) {
            "alpineArm32.tar"
        } else {
            throw RuntimeException("Unsupported Architecture")
        }
        
        fun chmodURW(filePath: String) {
            Debug("chmoding")
            val path = Paths.get(filePath)
            val currentPermissions = Files.getPosixFilePermissions(path)
            val newPermissions = currentPermissions + setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            Files.setPosixFilePermissions(path, newPermissions)
        }
        
        debug("downloading $AlpineUrl")
        val alpineTarFile = File(tempDir, alpineTar)
        downloadFileFromUrl(AlpineUrl, alpineTarFile)
        debug("decompressing")
        
        extractTar(alpineTarFile.absolutePath, rootfsDir.absolutePath)
        
        debug("decompress done")
        File(rootfsDir, "etc/hosts").writeText(hosts)
        File(rootfsDir, "etc/resolv.conf").writeText(nameserver)
        chmodURW("${rootfsDir.absolutePath}/proc")
        chmodURW("${rootfsDir.absolutePath}/dev")
        File(rootfsDir, "proc/.stat").writeText(stat)
        File(rootfsDir, "proc/.vmstat").writeText(vmstat)
        
        File(rootfsDir, "dev").apply { deleteRecursively();mkdirs() }
        File(rootfsDir, "proc").apply { deleteRecursively();mkdirs() }
        File(rootfsDir, "etc/environment").writeText(getAlpineEnviroment())
        
       
        
        chmodURW("${rootfsDir.absolutePath}/etc/passwd")
        chmodURW("${rootfsDir.absolutePath}/etc/shadow")
        chmodURW("${rootfsDir.absolutePath}/etc/group")
        Shell.SH.apply {
            debug("fixing ids")
            withContext(Dispatchers.IO){
                File(tempDir,"fixId.sh").writeText(readFromAssets(terminal, "fixId.sh"))
            }
            run("sh -c ${File(tempDir,"fixId.sh").absolutePath}").apply { println(stdout());println(stdout()) }
            shutdown()
        }
        
        
        val ProotUrl = if (supportedAbis.contains("x86_64")) {
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/prootX64.tar"
        } else if (supportedAbis.contains("x86")) {
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/prootX86.tar"
        } else if (supportedAbis.contains("arm64-v8a")) {
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/prootArm64.tar"
        } else if (supportedAbis.contains("armeabi-v7a")) {
            "https://raw.githubusercontent.com/RohitKushvaha01/Karbon-Packages/main/prootArm32.tar"
        } else {
            throw RuntimeException("Unsupported Architecture")
        }
        
        debug(ProotUrl)
        
        val ProotTar = if (supportedAbis.contains("x86_64")) {
            "prootX64.tar"
        } else if (supportedAbis.contains("x86")) {
            "prootX86.tar"
        } else if (supportedAbis.contains("arm64-v8a")) {
            "prootArm64.tar"
        } else if (supportedAbis.contains("armeabi-v7a")) {
            "prootArm32.tar"
        } else {
            throw RuntimeException("Unsupported Architecture")
        }
        
        debug(ProotTar)
        
        
        val prootTarFile = File(tempDir, ProotTar)
        debug("downloading at $prootTarFile")
        debug("donwloading $ProotUrl")
        downloadFileFromUrl(ProotUrl, prootTarFile)
        debug("decompressing")
        
        extractTar(prootTarFile.absolutePath, prootDir.parentFile.absolutePath)
        debug("proot done")
        
        extractScripts()
        
    }
    
    @Throws(Exception::class)
    fun readFromAssets(context: Context, fileName: String?): String {
        val stringBuilder = StringBuilder()
        var reader: BufferedReader? = null
        reader = BufferedReader(InputStreamReader(context.assets.open(fileName!!)))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            stringBuilder.append(line).append("\n")
        }
        reader.close()
        Debug("reading assest $stringBuilder")
        return stringBuilder.toString()
    }
    
    private suspend fun clean() {
        withContext(Dispatchers.IO) {
            tempDir.deleteRecursively()
            if (rootfsDir.exists()) {
                rootfsDir.deleteRecursively()
            }
            if (prootDir.exists()) {
                prootDir.deleteRecursively()
            }
        }
    }
    
    @Throws(Exception::class)
    private suspend fun downloadFileFromUrl(fileUrl: String, outputFile: File) {
        if (outputFile.exists().not()){
            withContext(Dispatchers.IO) {
                outputFile.parentFile.mkdirs()
                outputFile.createNewFile()
            }
        }
        withContext(Dispatchers.IO) {
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(outputFile)
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                debug("File $fileUrl downloaded successfully.")
            } else {
                throw RuntimeException("Failed to download file: HTTP ${connection.responseCode}")
            }
            
            connection.disconnect()
        }
    }
    
    private suspend fun extractTar(tarFilePath: String, outputDirPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val outputDir = File(outputDirPath)
            if (!outputDir.exists()) outputDir.mkdirs()
            
            val process = Runtime.getRuntime().exec(arrayOf("tar", "-xf", tarFilePath, "-C", outputDirPath))
            
            // Capture error output
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val errorOutput = StringBuilder()
            
            // Read error stream in a coroutine to prevent blocking
            launch {
                var line: String?
                while (errorReader.readLine().also { line = it } != null) {
                    errorOutput.append(line).append("\n")
                    debug("Tar extraction error: $line")
                }
            }
            
            // Capture standard output for progress monitoring
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            launch {
                var line: String?
                while (outputReader.readLine().also { line = it } != null) {
                    debug("Tar extraction progress: $line")
                }
            }
            
            // Wait for the process to complete and check result
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                debug("Tar extraction failed with exit code $exitCode")
                debug("Error output: ${errorOutput.toString()}")
                throw RuntimeException("Tar extraction failed with exit code $exitCode: ${errorOutput.toString()}")
            }
            
            // Verify the extraction
            val extractedFiles = outputDir.listFiles()
            if (extractedFiles == null || extractedFiles.isEmpty()) {
                throw RuntimeException("Tar extraction completed but no files were extracted")
            }
            
            exitCode == 0
        }
    }
    
    private suspend fun extractScripts(){
        withContext(Dispatchers.IO){
            exctractAssets(
                terminal,
                "proot.sh",
                "${terminal.filesDir.parentFile!!.absolutePath}/proot.sh",
            )
            exctractAssets(
                terminal,
                "init.sh",
                "${terminal.filesDir.parentFile!!.absolutePath}/rootfs/init.sh",
            )
            
            exctractAssets(terminal) {}
            
        }
    }
    
    
}