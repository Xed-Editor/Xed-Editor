package com.rk.settings.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.rk.exec.*
import com.rk.file.*
import com.rk.utils.application
import com.rk.utils.getTempDir
import java.io.File

// these checks are intended for trouble shooting terminal issues
@Composable
inline fun terminalChecks(): SnapshotStateList<Check> {
    return remember {
        mutableStateListOf<Check>(
            Check(
                label = "Check proot",
                run = { printLog ->
                    val libproot = File(application!!.applicationInfo.nativeLibraryDir, "libproot.so")
                    val prootloader = File(application!!.applicationInfo.nativeLibraryDir, "libloader.so")
                    val prootloader32 = File(application!!.applicationInfo.nativeLibraryDir, "libloader32.so")

                    printLog("Proot exists: ${libproot.exists()}")
                    printLog("Proot readable: ${libproot.canRead()}")
                    printLog("Proot executable: ${libproot.canExecute()}")
                    printLog("Proot Loader exists: ${prootloader.exists()}")
                    printLog("32bit Proot Loader exists: ${prootloader32.exists()}")

                    var exitCode = 999

                    try {
                        printLog("Creating a temporary sandbox environment...")

                        val process =
                            ProcessBuilder(libproot.absolutePath, "-0", "-r", "/", "true")
                                .apply {
                                    environment()["PROOT_TMP_DIR"] = getTempDir().absolutePath
                                    environment()["PROOT_LOADER"] = prootloader.absolutePath
                                    environment()["PROOT_LOADER_32"] = prootloader32.absolutePath
                                }
                                .start()

                        exitCode = process.waitFor()

                        printLog("Exit code: $exitCode")

                        if (exitCode != 0) {
                            val stderr = process.errorStream.bufferedReader().use { it.readText() }

                            if (stderr.isNotBlank()) {
                                printLog("stderr:")
                                printLog(stderr)
                            }
                        }
                    } catch (e: Exception) {
                        printLog("Error while running proot: ${e.message}")
                    }

                    libproot.exists() && exitCode == 0
                },
            ),
            Check(
                label = "Check System shell",
                run = { printLog ->
                    val shell = File("/system/bin/sh")
                    printLog("$shell exists: ${shell.exists()}")

                    val shell1 = File("/bin/sh")
                    printLog("$shell1 exists: ${shell1.exists()}")

                    printLog("$shell readable: ${shell.canRead()}")
                    printLog("$shell1 readable: ${shell1.canRead()}")

                    printLog("$shell executable: ${shell.canExecute()}")
                    printLog("$shell1 executable: ${shell1.canExecute()}")

                    var exitcode: Int = 999
                    try {
                        exitcode = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "true")).waitFor()
                        printLog("Exit code: $exitcode")
                    } catch (e: Exception) {
                        printLog("Error while running shell: ${e.message}")
                    }

                    exitcode == 0 &&
                        shell.exists() &&
                        shell1.exists() &&
                        shell.canRead() &&
                        shell1.canRead() &&
                        shell.canExecute() &&
                        shell1.canExecute()
                },
            ),
            Check(
                label = "Check Storage & Permissions",
                run = { printLog ->
                    val filesDir = application!!.filesDir
                    val totalSpace = filesDir.totalSpace / (1024 * 1024)
                    val freeSpace = filesDir.freeSpace / (1024 * 1024)
                    printLog("Internal Storage - Total: $totalSpace MB, Free: $freeSpace MB")

                    printLog("Files Dir: ${filesDir.absolutePath}")
                    printLog("Files Dir Writable: ${filesDir.canWrite()}")

                    val sandboxHome = sandboxHomeDir()
                    printLog("Sandbox Home: ${sandboxHome.absolutePath}")
                    printLog("Sandbox Home Writable: ${sandboxHome.canWrite()}")

                    if (freeSpace < 500) {
                        printLog("Warning: Low storage space (< 500 MB). Ubuntu might fail to install or update.")
                    }

                    freeSpace > 100 && filesDir.canWrite() && sandboxHome.canWrite()
                },
            ),
            Check(
                label = "Check Ubuntu",
                run = { printLog ->
                    if (!isTerminalInstalled()) {
                        printLog("Ubuntu not installed, skipping")
                        return@Check true
                    }

                    val rootfs = sandboxDir()
                    printLog("RootFS path: ${rootfs.absolutePath}")

                    val bash = rootfs.child("bin/bash")
                    printLog("Bash exists: ${bash.exists()}")
                    if (bash.exists()) {
                        printLog("Bash executable: ${bash.canExecute()}")
                    }

                    val apt = rootfs.child("usr/bin/apt")
                    printLog("Apt exists: ${apt.exists()}")

                    val osRelease = rootfs.child("etc/os-release")
                    if (osRelease.exists()) {
                        printLog("OS Release info:")
                        osRelease.readLines().take(5).forEach { printLog("  $it") }
                    }

                    printLog("Testing Ubuntu execution...")
                    var working = false
                    try {
                        val process = ubuntuProcess(command = listOf("true"))
                        val exitCode = process.waitFor()
                        val stderr = process.readStderr().trim()

                        printLog("Exit code: $exitCode")
                        if (stderr.isNotEmpty()) printLog("Stderr: $stderr")

                        working = (exitCode == 0)
                    } catch (e: Exception) {
                        printLog("Execution failed: ${e.message}")
                    }

                    working
                },
            ),
            Check(
                label = "Check Network Access",
                run = { printLog ->
                    if (!isTerminalInstalled()) {
                        printLog("Ubuntu not installed, skipping network check.")
                        return@Check true
                    }

                    printLog("Checking DNS resolution (google.com)...")
                    try {
                        val dnsProcess = ubuntuProcess(command = listOf("getent", "hosts", "google.com"))
                        val dnsExitCode = dnsProcess.waitFor()
                        if (dnsExitCode == 0) {
                            printLog("DNS resolution works.")
                        } else {
                            printLog("DNS resolution FAILED.")
                            val resolvConf = sandboxDir().child("etc/resolv.conf")
                            if (resolvConf.exists()) {
                                printLog("/etc/resolv.conf exists, content:")
                                resolvConf.readLines().forEach { printLog("  $it") }
                            } else {
                                printLog("/etc/resolv.conf is MISSING!")
                            }
                            printLog("Abnormality: Ubuntu will not have internet access without DNS.")
                        }
                        dnsExitCode == 0
                    } catch (e: Exception) {
                        printLog("Network check failed: ${e.message}")
                        false
                    }
                },
            ),
            Check(
                label = "Check Abnormalities",
                run = { printLog ->
                    if (!isTerminalInstalled()) {
                        printLog("Ubuntu not installed, skipping")
                        return@Check true
                    }
                    var abnormalities = 0

                    try {
                        val process = ubuntuProcess(command = listOf("touch", "/tmp/.test_xed"))
                        if (process.waitFor() == 0) {
                            ubuntuProcess(command = listOf("rm", "/tmp/.test_xed")).waitFor()
                        } else {
                            printLog("Abnormality: /tmp is not writable inside sandbox.")
                            abnormalities++
                        }
                    } catch (e: Exception) {
                        printLog("Error checking /tmp: ${e.message}")
                    }

                    if (abnormalities == 0) {
                        printLog("No major abnormalities detected.")
                    } else {
                        printLog("Found $abnormalities abnormality/ies.")
                    }

                    abnormalities == 0
                },
            ),
        )
    }
}
