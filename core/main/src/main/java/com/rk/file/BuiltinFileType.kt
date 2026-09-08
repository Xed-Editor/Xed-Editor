package com.rk.file

import com.rk.extension.api.XedExtensionPoint
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
private val nix = drawables.nix
private val xed = drawables.xed_editor

// TODO: Add icon for FileType.EXECUTABLE
// TODO: Add icon for FileType.PASCAL
// TODO: Add icon for FileType.ASSEMBLY
// TODO: Add icon for FileType.SMALI
// TODO: Add icon for FileType.LOG
// TODO: Add icon for FileType.ROCQ

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

    fun fromScope(scope: String?): FileType {
        if (scope == null) return BuiltinFileType.UNKNOWN
        return allTypes().firstOrNull { it.textmateScope == scope } ?: BuiltinFileType.UNKNOWN
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
    override val lspLanguageId: String?
) : FileType {
    // Web languages
    JAVASCRIPT(
        extensions = listOf("js", "mjs", "cjs", "jscsrc", "jshintrc","javascript"),
        textmateScope = "source.js",
        icon = Icon.ResourceIcon(js),
        title = "JavaScript",
        markdownNames = listOf("javascript"),
        lspLanguageId = "javascript"
    ),
    TYPESCRIPT(
        extensions = listOf("ts", "mts", "cts","typescript"),
        textmateScope = "source.ts",
        icon = Icon.ResourceIcon(ts),
        title = "TypeScript",
        markdownNames = listOf("typescript"),
        lspLanguageId = "typescript",
    ),
    JSX(
        extensions = listOf("jsx"),
        textmateScope = "source.js.jsx",
        icon = Icon.ResourceIcon(react),
        title = "JavaScript JSX",
        lspLanguageId = "javascriptreact",
    ),
    TSX(
        extensions = listOf("tsx"),
        textmateScope = "source.tsx",
        icon = Icon.ResourceIcon(react),
        title = "TypeScript JSX",
        lspLanguageId = "typescriptreact",
    ),
    HTML(
        extensions = listOf("html", "htm", "xhtml", "xht"),
        textmateScope = "text.html.basic",
        icon = Icon.ResourceIcon(html),
        title = "HTML",
        lspLanguageId = "html",
    ),
    HTMX(extensions = listOf("htmx"), textmateScope = "text.html.htmx", icon = Icon.ResourceIcon(html), title = "HTMX", lspLanguageId = "html"),
    CSS(extensions = listOf("css"), textmateScope = "source.css", icon = Icon.ResourceIcon(css), title = "CSS", lspLanguageId = "css"),
    SCSS(
        extensions = listOf("scss", "sass"),
        textmateScope = "source.css.scss",
        icon = Icon.ResourceIcon(sass),
        title = "SCSS",
        lspLanguageId = "scss",
    ),
    LESS(
        extensions = listOf("less"),
        textmateScope = "source.css.less",
        icon = Icon.ResourceIcon(less),
        title = "Less",
        lspLanguageId = "less",
    ),
    JSON(
        extensions = listOf("json", "jsonl", "jsonc"),
        textmateScope = "source.json",
        icon = Icon.ResourceIcon(json),
        title = "JSON",
        lspLanguageId = "json",
    ),
    MARKDOWN(
        extensions = listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdtxt", "mdwn"),
        textmateScope = "text.html.markdown",
        icon = Icon.ResourceIcon(markdown),
        title = "Markdown",
        lspLanguageId = "markdown",
    ),
    XML(
        extensions = listOf("xml", "xaml", "dtd", "plist", "ascx", "csproj", "wxi", "wxl", "wxs", "svg"),
        textmateScope = "text.xml",
        icon = Icon.ResourceIcon(xml),
        title = "XML",
        lspLanguageId = "xml",
    ),
    YAML(
        extensions = listOf("yaml", "yml", "eyaml", "eyml", "cff"),
        textmateScope = "source.yaml",
        icon = Icon.ResourceIcon(yaml),
        title = "YAML",
        lspLanguageId = "yaml",
    ),

    // Programming Languages
    PYTHON(
        extensions = listOf("py", "pyi"),
        textmateScope = "source.python",
        icon = Icon.ResourceIcon(python),
        title = "Python",
        markdownNames = listOf("python"),
        lspLanguageId = "python",
    ),
    JAVA(
        extensions = listOf("java", "jav", "bsh"),
        textmateScope = "source.java",
        icon = Icon.ResourceIcon(java),
        title = "Java",
        lspLanguageId = "java",
    ),
    GROOVY(
        extensions = listOf("gsh", "groovy", "gradle", "gvy", "gy"),
        textmateScope = "source.groovy",
        icon = Icon.ResourceIcon(groovy),
        iconOverride = mapOf("gradle" to Icon.ResourceIcon(gradle)),
        title = "Groovy",
        lspLanguageId = "groovy",
    ),
    C(extensions = listOf("c"), textmateScope = "source.c", icon = Icon.ResourceIcon(c), title = "C", lspLanguageId = "c"),
    CPP(
        extensions = listOf("cpp", "cxx", "cc", "c++", "h", "hpp", "hh", "hxx", "h++"),
        textmateScope = "source.cpp",
        icon = Icon.ResourceIcon(cpp),
        title = "C++",
        lspLanguageId = "cpp",
    ),
    CSHARP(
        extensions = listOf("cs", "csx"),
        textmateScope = "source.cs",
        icon = Icon.ResourceIcon(csharp),
        title = "C#",
        markdownNames = listOf("csharp"),
        lspLanguageId = "csharp",
    ),
    RUBY(
        extensions = listOf("rb", "erb", "gemspec"),
        textmateScope = "source.ruby",
        icon = Icon.ResourceIcon(ruby),
        title = "Ruby",
        markdownNames = listOf("ruby"),
        lspLanguageId = "ruby",
    ),
    LUA(extensions = listOf("lua", "luau"), textmateScope = "source.lua", icon = Icon.ResourceIcon(lua), title = "Lua", lspLanguageId = "lua"),
    GO(extensions = listOf("go"), textmateScope = "source.go", icon = Icon.ResourceIcon(go), title = "Go", lspLanguageId = "go"),
    PHP(extensions = listOf("php"), textmateScope = "source.php", icon = Icon.ResourceIcon(php), title = "PHP", lspLanguageId = "php"),
    RUST(
        extensions = listOf("rs"),
        textmateScope = "source.rust",
        icon = Icon.ResourceIcon(rust),
        title = "Rust",
        markdownNames = listOf("rust"),
        lspLanguageId = "rust",
    ),
    PASCAL(
        extensions = listOf("p", "pas"),
        textmateScope = "source.pascal",
        icon = null,
        title = "Pascal",
        markdownNames = listOf("pascal"),
        lspLanguageId = "pascal",
    ),
    ZIG(extensions = listOf("zig", "zon"), textmateScope = "source.zig", icon = Icon.ResourceIcon(zig), title = "Zig", lspLanguageId = "zig"),
    NIM(extensions = listOf("nim"), textmateScope = "source.nim", icon = Icon.ResourceIcon(nim), title = "Nim", lspLanguageId = "nim"),
    SWIFT(
        extensions = listOf("swift"),
        textmateScope = "source.swift",
        icon = Icon.ResourceIcon(swift),
        title = "Swift",
        lspLanguageId = "swift",
    ),
    DART(extensions = listOf("dart"), textmateScope = "source.dart", icon = Icon.ResourceIcon(dart), title = "Dart", lspLanguageId = "dart"),
    ROCQ(extensions = listOf("v", "coq"), textmateScope = "source.coq", icon = null, title = "Rocq (Coq)", lspLanguageId = "coq"),
    KOTLIN(
        extensions = listOf("kt", "kts"),
        textmateScope = "source.kotlin",
        icon = Icon.ResourceIcon(kotlin),
        title = "Kotlin",
        markdownNames = listOf("kotlin"),
        lspLanguageId = "kotlin",
    ),
    LISP(
        extensions = listOf("lisp", "clisp"),
        textmateScope = "source.lisp",
        icon = Icon.ResourceIcon(lisp),
        title = "Lisp",
        lspLanguageId = "lisp",
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
        lspLanguageId = "shellscript",
    ),
    WINDOWS_SHELL(
        extensions = listOf("cmd", "bat"),
        textmateScope = "source.batchfile",
        icon = Icon.ResourceIcon(shell),
        title = "Batch",
        lspLanguageId = "bat",
    ),
    POWERSHELL(
        extensions = listOf("ps1", "psm1", "psd1"),
        textmateScope = "source.powershell",
        icon = Icon.ResourceIcon(powershell),
        title = "PowerShell",
        markdownNames = listOf("powershell", "ps"),
        lspLanguageId = "powershell",
    ),
    SMALI(extensions = listOf("smali"), textmateScope = "source.smali", icon = null, title = "Smali", lspLanguageId = "smali"),
    ASSEMBLY(extensions = listOf("asm", "s", "S"), textmateScope = "source.asm", icon = null, title = "Assembly", lspLanguageId = "asm"),
    CMAKE(
        extensions = emptyList(),
        names = listOf("cmakelists.txt"),
        textmateScope = "source.cmake",
        icon = Icon.ResourceIcon(cmake),
        title = "CMake",
        lspLanguageId = "cmake",
    ),
    R(
        extensions = listOf("r"),
        textmateScope = "source.r",
        icon = Icon.ResourceIcon(r),
        title = "R",
        markdownNames = listOf("r"),
        lspLanguageId = "r",
    ),
    NIX(
        extensions = listOf("nix"),
        textmateScope = "source.nix",
        icon = Icon.ResourceIcon(nix),
        title = "Nix",
        lspLanguageId = "nix",
    ),

    // Data Files
    SQL(
        extensions = listOf("sql", "dsql", "sqllite"),
        textmateScope = "source.sql",
        icon = Icon.ResourceIcon(sql),
        title = "SQL",
        lspLanguageId = "sql",
    ),
    TOML(extensions = listOf("toml"), textmateScope = "source.toml", icon = Icon.ResourceIcon(toml), title = "TOML", lspLanguageId = "toml"),
    INI(extensions = listOf("ini"), textmateScope = "source.ini", icon = Icon.ResourceIcon(prop), title = "INI", lspLanguageId = "ini"),
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
        lspLanguageId = "properties",
    ),
    IGNORE(
        extensions = listOf("gitignore", "gitignore_global", "gitkeep", "git-blame-ignore-revs"),
        textmateScope = "source.ignore",
        icon = Icon.ResourceIcon(git),
        title = "Ignore",
        lspLanguageId = "gitignore",
    ),
    DIFF(
        extensions = listOf("diff", "patch", "rej"),
        textmateScope = "source.diff",
        icon = Icon.ResourceIcon(diff),
        title = "Diff",
        lspLanguageId = "diff",
    ),

    // Documents
    TEXT(
        extensions = listOf("txt"),
        textmateScope = null,
        icon = Icon.ResourceIcon(text),
        title = "Plain text",
        markdownNames = listOf("plaintext", "text"),
        lspLanguageId = "plaintext",
    ),
    LOG(extensions = listOf("log"), textmateScope = "text.log", icon = null, title = "Log", lspLanguageId = "log"),
    LATEX(
        extensions = listOf("latex", "tex", "ltx"),
        textmateScope = "text.tex.latex",
        icon = Icon.ResourceIcon(latex),
        title = "LaTeX",
        lspLanguageId = "latex",
    ),
    IMAGE(
        extensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "ico", "heic", "heif", "avif"),
        textmateScope = null,
        icon = Icon.ResourceIcon(image),
        title = "Image",
        lspLanguageId = null,
    ),
    AUDIO(
        extensions = listOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "wma", "opus"),
        textmateScope = null,
        icon = Icon.ResourceIcon(audio),
        title = "Audio",
        lspLanguageId = null,
    ),
    VIDEO(
        extensions = listOf("mp4", "avi", "mov", "mkv", "webm"),
        textmateScope = null,
        icon = Icon.ResourceIcon(video),
        title = "Video",
        lspLanguageId = null,
    ),
    ARCHIVE(
        extensions = listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xy"),
        textmateScope = null,
        icon = Icon.ResourceIcon(archive),
        title = "Archive",
        lspLanguageId = null,
    ),
    EXECUTABLE(
        extensions = listOf("exe", "dll", "so", "dylib", "bin"),
        textmateScope = null,
        icon = null,
        title = "Executable",
        lspLanguageId = null,
    ),
    APK(extensions = listOf("apk", "xapk", "apks"), textmateScope = null, icon = Icon.ResourceIcon(apk), title = "APK", lspLanguageId = null),
    XED_PACKAGE(
        extensions = listOf("xed"),
        textmateScope = null,
        icon = Icon.ResourceIcon(xed),
        title = strings.xed_package.getString(),
        lspLanguageId = null,
    ),
    UNKNOWN(extensions = emptyList(), textmateScope = null, icon = null, title = strings.unknown.getString(), lspLanguageId = null),
}
