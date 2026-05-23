package com.rk.file

import com.rk.extension.XedExtensionPoint
import com.rk.icons.Icon
import com.rk.icons.pack.currentIconPack
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

private val java = drawables.java
private val html = drawables.html
private val kotlin = drawables.kotlin
private val python = drawables.python
private val xml = drawables.xml
private val js = drawables.javascript
private val ts = drawables.typescript
private val lua = drawables.lua
private val extension = drawables.extension
private val prop = drawables.settings
private val c = drawables.c
private val cpp = drawables.cpp
private val json = drawables.json
private val css = drawables.css
private val csharp = drawables.csharp
private val shell = drawables.bash
private val apk = drawables.apk_document
private val archive = drawables.archive
private val text = drawables.text
private val video = drawables.video
private val audio = drawables.music
private val image = drawables.image
private val react = drawables.react
private val rust = drawables.rust
private val markdown = drawables.markdown
private val php = drawables.php
private val go = drawables.golang
private val lisp = drawables.lisp
private val sql = drawables.sql
private val groovy = drawables.apachegroovy
private val dart = drawables.dart
private val gradle = drawables.gradle
private val latex = drawables.latex
private val less = drawables.less
private val nim = drawables.nim
private val ruby = drawables.ruby
private val sass = drawables.sass
private val swift = drawables.swift
private val toml = drawables.toml
private val yaml = drawables.yaml
private val zig = drawables.zig
private val git = drawables.git
private val diff = drawables.diff
private val cmake = drawables.cmake
private val powershell = drawables.powershell
private val r = drawables.r

// TODO: Add icon for FileType.EXECUTABLE
// TODO: Add icon for FileType.PASCAL
// TODO: Add icon for FileType.ASSEMBLY
// TODO: Add icon for FileType.SMALI
// TODO: Add icon for FileType.LOG
// TODO: Add icon for FileType.ROCQ

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
}

/**
 * Manager responsible for handling file type registration and resolution.
 *
 * This object maintains a registry of both built-in [BuiltinFileType]s and dynamically registered [FileType]s via
 * extensions. It provides utility methods to identify a file's type based on its name, extension, or Markdown language
 * identifier.
 */
object FileTypeManager {
    private val dynamicRegistry = mutableListOf<FileType>()

    /** Register a new file type dynamically. */
    @XedExtensionPoint
    fun register(fileType: FileType) {
        if (!dynamicRegistry.contains(fileType)) {
            dynamicRegistry.add(fileType)
        }
    }

    /** Unregister a file type. */
    @XedExtensionPoint
    fun unregister(fileType: FileType) {
        dynamicRegistry.remove(fileType)
    }

    /** Get all dynamically registered file types + built-in file types together */
    fun allTypes(): List<FileType> = BuiltinFileType.entries + dynamicRegistry

    fun fromFileName(name: String): FileType {
        val normalized = name.lowercase()
        val fileExt = normalized.substringAfterLast('.', "")
        return allTypes().firstOrNull { it.names != null && normalized in it.names!! } ?: fromExtension(fileExt)
    }

    fun fromExtension(ext: String): FileType {
        val normalized = ext.lowercase().removePrefix(".")
        return allTypes().firstOrNull { normalized in it.extensions } ?: BuiltinFileType.UNKNOWN
    }

    fun fromMarkdownName(name: String): FileType {
        val normalized = name.lowercase()
        return allTypes().firstOrNull { normalized in it.extensions || normalized in it.markdownNames }
            ?: BuiltinFileType.UNKNOWN
    }

    fun knowsExtension(ext: String): Boolean {
        val normalized = ext.lowercase().removePrefix(".")
        return allTypes().any { normalized in it.extensions }
    }
}

/** Enum representing all built-in [FileType]s in Xed-Editor. */
enum class BuiltinFileType(
    override val extensions: List<String>,
    override val names: List<String>? = null,
    override val textmateScope: String?,
    override val icon: Icon?,
    override val iconOverride: Map<String, Icon>? = null,
    override val title: String,
    override val markdownNames: List<String> = emptyList(),
) : FileType {
    // Web languages
    JAVASCRIPT(
        extensions = listOf("js", "mjs", "cjs", "jscsrc", "jshintrc", "mut"),
        textmateScope = "source.js",
        icon = Icon.ResourceIcon(js),
        title = "JavaScript",
        markdownNames = listOf("javascript"),
    ),
    TYPESCRIPT(
        extensions = listOf("ts", "mts", "cts"),
        textmateScope = "source.ts",
        icon = Icon.ResourceIcon(ts),
        title = "TypeScript",
        markdownNames = listOf("typescript"),
    ),
    JSX(
        extensions = listOf("jsx"),
        textmateScope = "source.js.jsx",
        icon = Icon.ResourceIcon(react),
        title = "JavaScript JSX",
    ),
    TSX(
        extensions = listOf("tsx"),
        textmateScope = "source.tsx",
        icon = Icon.ResourceIcon(react),
        title = "TypeScript JSX",
    ),
    HTML(
        extensions = listOf("html", "htm", "xhtml", "xht"),
        textmateScope = "text.html.basic",
        icon = Icon.ResourceIcon(html),
        title = "HTML",
    ),
    HTMX(extensions = listOf("htmx"), textmateScope = "text.html.htmx", icon = Icon.ResourceIcon(html), title = "HTMX"),
    CSS(extensions = listOf("css"), textmateScope = "source.css", icon = Icon.ResourceIcon(css), title = "CSS"),
    SCSS(
        extensions = listOf("scss", "sass"),
        textmateScope = "source.css.scss",
        icon = Icon.ResourceIcon(sass),
        title = "SCSS",
    ),
    LESS(
        extensions = listOf("less"),
        textmateScope = "source.css.less",
        icon = Icon.ResourceIcon(less),
        title = "Less",
    ),
    JSON(
        extensions = listOf("json", "jsonl", "jsonc"),
        textmateScope = "source.json",
        icon = Icon.ResourceIcon(json),
        title = "JSON",
    ),
    MARKDOWN(
        extensions = listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdtxt", "mdwn"),
        textmateScope = "text.html.markdown",
        icon = Icon.ResourceIcon(markdown),
        title = "Markdown",
    ),
    XML(
        extensions = listOf("xml", "xaml", "dtd", "plist", "ascx", "csproj", "wxi", "wxl", "wxs", "svg"),
        textmateScope = "text.xml",
        icon = Icon.ResourceIcon(xml),
        title = "XML",
    ),
    YAML(
        extensions = listOf("yaml", "yml", "eyaml", "eyml", "cff"),
        textmateScope = "source.yaml",
        icon = Icon.ResourceIcon(yaml),
        title = "YAML",
    ),

    // Programming Languages
    PYTHON(
        extensions = listOf("py", "pyi"),
        textmateScope = "source.python",
        icon = Icon.ResourceIcon(python),
        title = "Python",
        markdownNames = listOf("python"),
    ),
    JAVA(
        extensions = listOf("java", "jav", "bsh"),
        textmateScope = "source.java",
        icon = Icon.ResourceIcon(java),
        title = "Java",
    ),
    GROOVY(
        extensions = listOf("gsh", "groovy", "gradle", "gvy", "gy"),
        textmateScope = "source.groovy",
        icon = Icon.ResourceIcon(groovy),
        iconOverride = mapOf("gradle" to Icon.ResourceIcon(gradle)),
        title = "Groovy",
    ),
    C(extensions = listOf("c"), textmateScope = "source.c", icon = Icon.ResourceIcon(c), title = "C"),
    CPP(
        extensions = listOf("cpp", "cxx", "cc", "c++", "h", "hpp", "hh", "hxx", "h++"),
        textmateScope = "source.cpp",
        icon = Icon.ResourceIcon(cpp),
        title = "C++",
    ),
    CSHARP(
        extensions = listOf("cs", "csx"),
        textmateScope = "source.cs",
        icon = Icon.ResourceIcon(csharp),
        title = "C#",
        markdownNames = listOf("csharp"),
    ),
    RUBY(
        extensions = listOf("rb", "erb", "gemspec"),
        textmateScope = "source.ruby",
        icon = Icon.ResourceIcon(ruby),
        title = "Ruby",
        markdownNames = listOf("ruby"),
    ),
    LUA(extensions = listOf("lua", "luau"), textmateScope = "source.lua", icon = Icon.ResourceIcon(lua), title = "Lua"),
    GO(extensions = listOf("go"), textmateScope = "source.go", icon = Icon.ResourceIcon(go), title = "Go"),
    PHP(extensions = listOf("php"), textmateScope = "source.php", icon = Icon.ResourceIcon(php), title = "PHP"),
    RUST(
        extensions = listOf("rs"),
        textmateScope = "source.rust",
        icon = Icon.ResourceIcon(rust),
        title = "Rust",
        markdownNames = listOf("rust"),
    ),
    PASCAL(
        extensions = listOf("p", "pas"),
        textmateScope = "source.pascal",
        icon = null,
        title = "Pascal",
        markdownNames = listOf("pascal"),
    ),
    ZIG(extensions = listOf("zig"), textmateScope = "source.zig", icon = Icon.ResourceIcon(zig), title = "Zig"),
    NIM(extensions = listOf("nim"), textmateScope = "source.nim", icon = Icon.ResourceIcon(nim), title = "Nim"),
    SWIFT(
        extensions = listOf("swift"),
        textmateScope = "source.swift",
        icon = Icon.ResourceIcon(swift),
        title = "Swift",
    ),
    DART(extensions = listOf("dart"), textmateScope = "source.dart", icon = Icon.ResourceIcon(dart), title = "Dart"),
    ROCQ(extensions = listOf("v", "coq"), textmateScope = "source.coq", icon = null, title = "Rocq (Coq)"),
    KOTLIN(
        extensions = listOf("kt", "kts"),
        textmateScope = "source.kotlin",
        icon = Icon.ResourceIcon(kotlin),
        title = "Kotlin",
        markdownNames = listOf("kotlin"),
    ),
    LISP(
        extensions = listOf("lisp", "clisp"),
        textmateScope = "source.lisp",
        icon = Icon.ResourceIcon(lisp),
        title = "Lisp",
    ),
    SHELL(
        extensions =
            listOf(
                "sh",
                "bash",
                "bash_login",
                "bash_logout",
                "bash_profile",
                "bashrc",
                "profile",
                "rhistory",
                "rprofile",
                "zsh",
                "zlogin",
                "zlogout",
                "zprofile",
                "zshenv",
                "zshrc",
                "fish",
                "ksh",
            ),
        textmateScope = "source.shell",
        icon = Icon.ResourceIcon(shell),
        title = "Shell script",
        markdownNames = listOf("shell", "console"),
    ),
    WINDOWS_SHELL(
        extensions = listOf("cmd", "bat"),
        textmateScope = "source.batchfile",
        icon = Icon.ResourceIcon(shell),
        title = "Batch",
    ),
    POWERSHELL(
        extensions = listOf("ps1", "psm1", "psd1"),
        textmateScope = "source.powershell",
        icon = Icon.ResourceIcon(powershell),
        title = "PowerShell",
        markdownNames = listOf("powershell", "ps"),
    ),
    SMALI(extensions = listOf("smali"), textmateScope = "source.smali", icon = null, title = "Smali"),
    ASSEMBLY(extensions = listOf("asm"), textmateScope = "source.asm", icon = null, title = "Assembly"),
    CMAKE(
        extensions = emptyList(),
        names = listOf("cmakelists.txt"),
        textmateScope = "source.cmake",
        icon = Icon.ResourceIcon(cmake),
        title = "CMake",
    ),
    R(
        extensions = listOf("r"),
        textmateScope = "source.r",
        icon = Icon.ResourceIcon(r),
        title = "R",
        markdownNames = listOf("r"),
    ),

    // Data Files
    SQL(
        extensions = listOf("sql", "dsql", "sqllite"),
        textmateScope = "source.sql",
        icon = Icon.ResourceIcon(sql),
        title = "SQL",
    ),
    TOML(extensions = listOf("toml"), textmateScope = "source.toml", icon = Icon.ResourceIcon(toml), title = "TOML"),
    INI(extensions = listOf("ini"), textmateScope = "source.ini", icon = Icon.ResourceIcon(prop), title = "INI"),
    PROPERTIES(
        extensions =
            listOf("properties", "cfg", "conf", "config", "editorconfig", "gitconfig", "gitmodules", "gitattributes"),
        textmateScope = "source.properties",
        icon = Icon.ResourceIcon(prop),
        iconOverride =
            mapOf(
                "gitmodules" to Icon.ResourceIcon(git),
                "gitattributes" to Icon.ResourceIcon(git),
                "gitconfig" to Icon.ResourceIcon(git),
            ),
        title = "Properties",
    ),
    IGNORE(
        extensions = listOf("gitignore", "gitignore_global", "gitkeep", "git-blame-ignore-revs"),
        textmateScope = "source.ignore",
        icon = Icon.ResourceIcon(git),
        title = "Ignore",
    ),
    DIFF(
        extensions = listOf("diff", "patch", "rej"),
        textmateScope = "source.diff",
        icon = Icon.ResourceIcon(diff),
        title = "Diff",
    ),

    // Documents
    TEXT(
        extensions = listOf("txt"),
        textmateScope = null,
        icon = Icon.ResourceIcon(text),
        title = "Plain text",
        markdownNames = listOf("plaintext", "text"),
    ),
    LOG(extensions = listOf("log"), textmateScope = "text.log", icon = null, title = "Log"),
    LATEX(
        extensions = listOf("latex", "tex", "ltx"),
        textmateScope = "text.tex.latex",
        icon = Icon.ResourceIcon(latex),
        title = "LaTeX",
    ),
    IMAGE(
        extensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "ico", "heic", "heif", "avif"),
        textmateScope = null,
        icon = Icon.ResourceIcon(image),
        title = "Image",
    ),
    AUDIO(
        extensions = listOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "wma", "opus"),
        textmateScope = null,
        icon = Icon.ResourceIcon(audio),
        title = "Audio",
    ),
    VIDEO(
        extensions = listOf("mp4", "avi", "mov", "mkv", "webm"),
        textmateScope = null,
        icon = Icon.ResourceIcon(video),
        title = "Video",
    ),
    ARCHIVE(
        extensions = listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xy"),
        textmateScope = null,
        icon = Icon.ResourceIcon(archive),
        title = "Archive",
    ),
    EXECUTABLE(
        extensions = listOf("exe", "dll", "so", "dylib", "bin"),
        textmateScope = null,
        icon = null,
        title = "Executable",
    ),
    APK(extensions = listOf("apk", "xapk", "apks"), textmateScope = null, icon = Icon.ResourceIcon(apk), title = "APK"),
    UNKNOWN(extensions = emptyList(), textmateScope = null, icon = null, title = strings.unknown.getString()),
}
