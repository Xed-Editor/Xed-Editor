package com.rk.exec

import android.content.Context
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.localLibDir
import com.rk.file.sandboxHomeDir
import com.rk.utils.application
import com.rk.utils.getSourceDirOfPackage
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import java.io.File

object ProcessEnv {
    fun getEnvironment(context: Context, tmpDir: File, workingDir: String, linker: String): Map<String, String> {
        val prefixPath = File(context.filesDir, "usr").absolutePath
        val env = mutableMapOf<String, String>()
        
        env["PREFIX"] = prefixPath
        env["LD_PRELOAD"] = "$prefixPath/lib/libtermux-exec.so"
        env["PROOT"] = "${context.applicationInfo.nativeLibraryDir}/libproot.so"
        env["PROOT_LOADER"] = "${context.applicationInfo.nativeLibraryDir}/libloader.so"
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["WKDIR"] = workingDir
        env["COLORTERM"] = "truecolor"
        env["TERM"] = "xterm-256color"
        env["LANG"] = "C.UTF-8"
        env["PUBLIC_HOME"] = context.getExternalFilesDir(null)?.absolutePath.orEmpty()
        env["DEBUG"] = BuildConfig.DEBUG.toString()
        env["LOCAL"] = localDir(context).absolutePath
        env["PRIVATE_DIR"] = context.filesDir.parentFile!!.absolutePath
        env["EXT_HOME"] = sandboxHomeDir(context).absolutePath
        env["HOME"] = sandboxHomeDir(context).absolutePath
        env["PROMPT_DIRTRIM"] = "2"
        env["LINKER"] = linker
        env["NATIVE_LIB_DIR"] = context.applicationInfo.nativeLibraryDir
        env["TMP_DIR"] = getTempDir().absolutePath
        env["TMPDIR"] = getTempDir().absolutePath
        env["TZ"] = "UTC"
        env["DOTNET_GCHeapHardLimit"] = "1C0000000"
        env["SOURCE_DIR"] = context.applicationInfo.sourceDir
        env["TERMUX_X11_SOURCE_DIR"] = getSourceDirOfPackage(application!!, "com.termux.x11").orEmpty()
        env["DISPLAY"] = ":0"
        env["LD_LIBRARY_PATH"] = "$prefixPath/lib:${localLibDir(context).absolutePath}"

        val loader32 = "${context.applicationInfo.nativeLibraryDir}/libloader32.so"
        if (File(loader32).exists()) {
            env["PROOT_LOADER_32"] = loader32
        }

        env["ANDROID_ART_ROOT"] = System.getenv("ANDROID_ART_ROOT").orEmpty()
        env["ANDROID_DATA"] = System.getenv("ANDROID_DATA").orEmpty()
        env["ANDROID_I18N_ROOT"] = System.getenv("ANDROID_I18N_ROOT").orEmpty()
        env["ANDROID_ROOT"] = System.getenv("ANDROID_ROOT").orEmpty()
        env["ANDROID_RUNTIME_ROOT"] = System.getenv("ANDROID_RUNTIME_ROOT").orEmpty()
        env["ANDROID_TZDATA_ROOT"] = System.getenv("ANDROID_TZDATA_ROOT").orEmpty()
        env["BOOTCLASSPATH"] = System.getenv("BOOTCLASSPATH").orEmpty()
        env["DEX2OATBOOTCLASSPATH"] = System.getenv("DEX2OATBOOTCLASSPATH").orEmpty()
        env["EXTERNAL_STORAGE"] = System.getenv("EXTERNAL_STORAGE").orEmpty()
        env["PATH"] = "$prefixPath/bin:$prefixPath/bin/applets:${System.getenv("PATH")}:${localBinDir(context).absolutePath}"

        return env
    }

    fun resolvePath(arg: String, prefixPath: String, homePath: String): String {
        var resolved = arg
        if (resolved.startsWith("/home/")) {
            resolved = homePath + "/" + resolved.substring(6)
        } else if (resolved == "/home") {
            resolved = homePath
        }
        if (resolved.startsWith("/usr/bin/")) {
            resolved = prefixPath + "/bin/" + resolved.substring(9)
        } else if (resolved.startsWith("/usr/")) {
            resolved = prefixPath + "/" + resolved.substring(5)
        } else if (resolved.startsWith("/bin/")) {
            resolved = prefixPath + "/bin/" + resolved.substring(5)
        }
        return resolved
    }
}
