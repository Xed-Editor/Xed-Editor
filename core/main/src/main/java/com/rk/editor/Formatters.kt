package com.rk.editor

import com.rk.extension.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.settings.Preference
import com.rk.settings.Settings
import io.github.rosemoe.sora.lang.format.Formatter

abstract class FormatterProvider {
    abstract val id: String
    abstract val label: String
    abstract val supportedExtensions: List<String>
    abstract val icon: Icon?

    abstract fun getFormatter(): Formatter
}

sealed class FormatterSource {
    data object LSP : FormatterSource()

    data class EXTENSION(val formatter: FormatterProvider) : FormatterSource()
}

object Formatters {
    const val LSP_FORMATTER_ID = "lsp"

    private val _providers = mutableListOf<FormatterProvider>()
    val providers: List<FormatterProvider>
        get() = _providers.toList()

    @XedExtensionPoint
    fun registerFormatter(provider: FormatterProvider) {
        if (!_providers.contains(provider)) {
            _providers.add(provider)
        }
    }

    @XedExtensionPoint
    fun unregisterFormatter(provider: FormatterProvider) {
        _providers.remove(provider)
    }

    fun getSourceForId(id: String): FormatterSource? {
        if (id == LSP_FORMATTER_ID) return FormatterSource.LSP

        providers
            .find { it.id == id }
            ?.also {
                return FormatterSource.EXTENSION(it)
            }
        return null
    }

    fun getIdOf(source: FormatterSource): String {
        return when (source) {
            is FormatterSource.LSP -> "lsp"
            is FormatterSource.EXTENSION -> source.formatter.id
        }
    }

    fun isProviderEnabled(provider: FormatterProvider): Boolean {
        return Preference.getBoolean("formatter_${provider.id}", true)
    }

    fun setProviderEnabled(formatter: FormatterProvider, enabled: Boolean) {
        Preference.setBoolean("formatter_${formatter.id}", enabled)
    }

    fun getPreferredSourceForFile(file: FileObject): FormatterSource? {
        val formatterIds = Settings.formatters.split("|").toTypedArray()
        val formatters = formatterIds.mapNotNull { id -> getSourceForId(id) }

        for (formatterType in formatters) {
            when (formatterType) {
                is FormatterSource.LSP -> return formatterType
                is FormatterSource.EXTENSION -> {
                    val formatter = formatterType.formatter
                    val fileExt = file.getExtension()
                    val isSupported = formatter.supportedExtensions.contains(fileExt)

                    if (isSupported) return formatterType
                }
            }
        }

        return null
    }

    fun isLspFormatterEnabled(): Boolean {
        return Preference.getBoolean("formatter_$LSP_FORMATTER_ID", true)
    }

    fun setLspFormatterEnabled(enabled: Boolean) {
        Preference.setBoolean("formatter_$LSP_FORMATTER_ID", enabled)
    }
}
