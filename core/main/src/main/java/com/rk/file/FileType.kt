package com.rk.file

import com.rk.icons.Icon
import com.rk.icons.pack.currentIconPack
import com.rk.resources.drawables

/**
 * Interface representing a file type and its associated metadata.
 *
 * This interface defines the contract for identifying files and providing syntax highlighting information.
 *
 * @property extensions A list of file extensions associated with this file type (without the leading dot).
 * @property names An optional list of specific file names associated with this file type (e.g., "cmakelists.txt").
 * @property textmateScope The TextMate scope string used for syntax highlighting (e.g., "source.kt"). Null if not
 *   applicable.
 * @property icon The resource ID of the default icon for this file type. Null if no icon is available.
 * @property iconOverride A map of specific extensions to specific icon resource IDs for fine-grained icon control.
 * @property name The short identifier name of the file type.
 * @property title A human-readable title for the file type.
 * @property markdownNames A list of language identifiers used in Markdown code blocks.
 * @property lspLanguageId The language identifier sent to the language server in the `didOpen` notification
 *   (e.g., "typescriptreact"). Null if the file type does not support LSP.
 */
interface FileType {
    val extensions: List<String>
    val names: List<String>?
        get() = null

    val textmateScope: String?
    val icon: Icon?
    val iconOverride: Map<String, Icon>?
        get() = null

    val name: String

    val title: String

    /**
     * Language identifiers used in Markdown code blocks. Should only include additional names that are not included in
     * the extensions list.
     */
    val markdownNames: List<String>
        get() = emptyList()

    /**
     * Retrieves an icon for this FileType. The icon is not tinted.
     *
     * Supports:
     * - ✔ Icon pack (uses the icon from the icon pack if available, otherwise uses the builtin icon)
     * - ✘ Tint (applyTint property in icon pack or builtin icon tint)
     *
     * @return An [Icon] representing the file type icon.
     */
    fun getResolvedIcon(): Icon {
        val iconPackFile = currentIconPack.value?.getIconFileForFileType(this)
        return iconPackFile?.let { Icon.SvgIcon(it) } ?: icon ?: Icon.ResourceIcon(drawables.file)
    }

    /**
     * The LSP language identifier for this file type. Defaults to null so that custom/third-party implementations of
     * [FileType] do not have to provide it.
     */
    val lspLanguageId: String?
        get() = null
}
