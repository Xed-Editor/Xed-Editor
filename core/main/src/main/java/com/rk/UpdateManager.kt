package com.rk

import androidx.core.content.pm.PackageInfoCompat
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.editor.DEFAULT_EXTRA_KEYS_SYMBOLS
import com.rk.utils.application
import com.rk.utils.hasHardwareKeyboard
import com.rk.xededitor.BuildConfig

object UpdateManager {
    private fun deleteCommonFiles() =
        with(application!!) {
            codeCacheDir.apply {
                if (exists()) {
                    deleteRecursively()
                }
            }

            localBinDir().apply {
                if (exists()) {
                    deleteRecursively()
                }
            }
        }

    fun inspect() =
        with(application!!) {
            val lastVersionCode = Settings.last_version_code
            val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

            if (lastVersionCode != currentVersionCode) {
                // App is updated -> Migrate existing files
                if (lastVersionCode <= 40L) {
                    Preference.clearData()
                }

                if (lastVersionCode <= 66L) {
                    Settings.line_spacing = 1f
                }

                if (lastVersionCode <= 68L) {
                    val rootfs =
                        sandboxDir().listFiles()?.filter {
                            it.absolutePath != sandboxHomeDir().absolutePath &&
                                it.absolutePath != sandboxDir().child("tmp").absolutePath
                        } ?: emptyList()

                    if (rootfs.isNotEmpty()) {
                        localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").createNewFile()
                    }
                }

                if (lastVersionCode <= 69L) {
                    sandboxDir().child(".cache/.packages_ensured").apply {
                        if (exists()) {
                            delete()
                        }
                    }
                }

                if (lastVersionCode <= 73) {
                    runCatching {
                        val filesToCopy = application!!.cacheDir.listFiles { it.isFile && it.extension.isEmpty() }
                        filesToCopy?.forEach { it.copyTo(application!!.filesDir.child(it.name), overwrite = true) }
                    }
                }

                if (lastVersionCode <= 76) {
                    runCatching {
                        val filesToCopy = application!!.filesDir.listFiles { it.isFile && it.extension.isEmpty() }
                        filesToCopy?.forEach { it.delete() }
                    }
                }

                if (lastVersionCode <= 80) {
                    val oldReadOnly = Preference.getBoolean("readOnly", false)
                    Preference.removeKey("readOnly")
                    if (oldReadOnly) {
                        Preference.setBoolean("read_only_default", true)
                    }

                    val oldShownDisclaimer = Preference.getBoolean("shownDisclaimer", false)
                    Preference.removeKey("shownDisclaimer")
                    if (oldShownDisclaimer) {
                        Preference.setBoolean("shown_disclaimer", true)
                    }

                    val oldOled = Preference.getBoolean("oled", false)
                    Preference.removeKey("oled")
                    if (oldOled) {
                        Preference.setBoolean("amoled", true)
                    }

                    val oldPinLine = Preference.getBoolean("pinline", false)
                    Preference.removeKey("pinline")
                    if (oldPinLine) {
                        Preference.setBoolean("pin_line_number", true)
                    }

                    val oldWWTxt = Preference.getBoolean("ww_txt", true)
                    Preference.removeKey("ww_txt")
                    if (!oldWWTxt) {
                        Preference.setBoolean("word_wrap_text", false)
                    }

                    val oldWordWrap = Preference.getBoolean("wordwrap", false)
                    Preference.removeKey("wordwrap")
                    if (oldWordWrap) {
                        Preference.setBoolean("word_wrap", true)
                    }

                    val default = hasHardwareKeyboard(application!!).not()
                    val oldArrowKeys = Preference.getBoolean("arrow_keys", default)
                    Preference.removeKey("arrow_keys")
                    if (oldArrowKeys != default) {
                        Preference.setBoolean("show_extra_keys", oldArrowKeys)
                    }

                    val oldStrictMode = Preference.getBoolean("strictMode", BuildConfig.DEBUG)
                    Preference.removeKey("strictMode")
                    if (oldStrictMode != BuildConfig.DEBUG) {
                        Preference.setBoolean("strict_mode", oldStrictMode)
                    }

                    val oldTerminalVirusNotice = Preference.getBoolean("terminal-virus-notice", false)
                    Preference.removeKey("terminal-virus-notice")
                    if (oldTerminalVirusNotice) {
                        Preference.setBoolean("terminal_virus_notice", true)
                    }

                    val oldTextMateSuggestion = Preference.getBoolean("textMateSuggestion", true)
                    Preference.removeKey("textMateSuggestion")
                    if (!oldTextMateSuggestion) {
                        Preference.setBoolean("textmate_suggestions", true)
                    }

                    val oldDesktopMode = Preference.getBoolean("desktopMode", false)
                    Preference.removeKey("desktopMode")
                    if (oldDesktopMode) {
                        Preference.setBoolean("desktop_mode", true)
                    }

                    val oldTabSize = Preference.getInt("tabsize", 4)
                    Preference.removeKey("tabsize")
                    if (oldTabSize != 4) {
                        Preference.setInt("tab_size", oldTabSize)
                    }

                    val oldTextSize = Preference.getInt("textsize", 14)
                    Preference.removeKey("textsize")
                    if (oldTextSize != 14) {
                        Preference.setInt("text_size", oldTextSize)
                    }

                    val defaultLang = application!!.resources.configuration.locales[0].language
                    val oldCurrentLang = Preference.getString("currentLang", defaultLang)
                    Preference.removeKey("currentLang")
                    if (oldCurrentLang != defaultLang) {
                        Preference.setString("current_lang", oldCurrentLang)
                    }

                    val oldExtraKeys = Preference.getString("extra_keys", DEFAULT_EXTRA_KEYS_SYMBOLS)
                    Preference.removeKey("extra_keys")
                    if (oldExtraKeys != DEFAULT_EXTRA_KEYS_SYMBOLS) {
                        Preference.setString("extra_keys_symbols", oldExtraKeys)
                    }
                }

                deleteCommonFiles()
            }

            Settings.last_version_code = currentVersionCode
        }
}
