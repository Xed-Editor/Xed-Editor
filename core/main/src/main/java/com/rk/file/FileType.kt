package com.rk.file

import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

private val java = drawables.java
private val html = drawables.ic_language_html
private val kotlin = drawables.ic_language_kotlin
private val python = drawables.ic_language_python
private val xml = drawables.ic_language_xml
private val js = drawables.ic_language_js
private val ts = drawables.typescript
private val lua = drawables.lua
private val plugin = drawables.extension
private val prop = drawables.settings
private val c = drawables.ic_language_c
private val cpp = drawables.ic_language_cpp
private val json = drawables.ic_language_json
private val css = drawables.ic_language_css
private val csharp = drawables.ic_language_csharp
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

// TODO: Add icon for FileType.POWERSHELL
// TODO: Add icon for FileType.EXECUTABLE
// TODO: Add icon for FileType.PASCAL
// TODO: Add icon for FileType.ASSEMBLY
// TODO: Add icon for FileType.SMALI
// TODO: Add icon for FileType.LOG
// TODO: Add icon for FileType.ROCQ

enum class FileType(
    val extensions: List<String>,
    val textmateScope: String?,
    val icon: Int?,
    val iconOverride: Map<String, Int>? = null,
    val title: String
) {
    // Web languages
    JAVASCRIPT(
        extensions = listOf("js", "mjs", "cjs", "jscsrc", "jshintrc", "mut"),
        textmateScope = "source.js",
        icon = js,
        title = "JavaScript"
    ),
    TYPESCRIPT(
        extensions = listOf("ts"),
        textmateScope = "source.ts",
        icon = ts,
        title = "TypeScript"
    ),
    JSX(
        extensions = listOf("jsx"),
        textmateScope = "source.js.jsx",
        icon = react,
        title = "JavaScript JSX"
    ),
    TSX(
        extensions = listOf("jsx"),
        textmateScope = "source.tsx",
        icon = react,
        title = "TypeScript JSX"
    ),
    HTML(
        extensions = listOf("html", "htm", "xhtml", "xht"),
        textmateScope = "text.html.basic",
        icon = html,
        title = "HTML"
    ),
    HTMX(
        extensions = listOf("htmx"),
        textmateScope = "text.html.htmx",
        icon = html,
        title = "HTMX"
    ),
    CSS(
        extensions = listOf("css"),
        textmateScope = "source.css",
        icon = css,
        title = "CSS"
    ),
    SCSS(
        extensions = listOf("scss", "sass"),
        textmateScope = "source.css.scss",
        icon = sass,
        title = "SCSS"
    ),
    LESS(
        extensions = listOf("less"),
        textmateScope = "source.css.less",
        icon = less,
        title = "Less"
    ),
    JSON(
        extensions = listOf("json", "jsonl", "jsonc"),
        textmateScope = "source.json",
        icon = json,
        title = "JSON"
    ),
    MARKDOWN(
        extensions = listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdtxt", "mdwn"),
        textmateScope = "text.html.markdown",
        icon = markdown,
        title = "Markdown"
    ),
    XML(
        extensions = listOf("xml", "xaml", "dtd", "plist", "ascx", "csproj", "wxi", "wxl", "wxs", "svg"),
        textmateScope = "text.xml",
        icon = xml,
        title = "XML"
    ),
    YAML(
        extensions = listOf("yaml", "yml", "eyaml", "eyml", "cff"),
        textmateScope = "source.yaml",
        icon = yaml,
        title = "YAML"
    ),

    // Programming Languages
    PYTHON(
        extensions = listOf("py", "pyi"),
        textmateScope = "source.python",
        icon = python,
        title = "Python"
    ),
    JAVA(
        extensions = listOf("java", "jav", "bsh"),
        textmateScope = "source.java",
        icon = java,
        title = "Java"
    ),
    GROOVY(
        extensions = listOf("gsh", "groovy", "gradle", "gvy", "gy"),
        textmateScope = "source.groovy",
        icon = groovy,
        iconOverride = mapOf("gradle" to gradle),
        title = "Groovy"
    ),
    C(
        extensions = listOf("c"),
        textmateScope = "source.c",
        icon = c,
        title = "C"
    ),
    CPP(
        extensions = listOf("cpp", "cxx", "cc", "c++", "h", "hpp", "hh", "hxx", "h++"),
        textmateScope = "source.cpp",
        icon = cpp,
        title = "C++"
    ),
    CSHARP(
        extensions = listOf("cs", "csx"),
        textmateScope = "source.cs",
        icon = csharp,
        title = "C#"
    ),
    RUBY(
        extensions = listOf("rb", "erb", "gemspec"),
        textmateScope = "source.ruby",
        icon = ruby,
        title = "Ruby"
    ),
    LUA(
        extensions = listOf("lua"),
        textmateScope = "source.lua",
        icon = lua,
        title = "Lua"
    ),
    GO(
        extensions = listOf("go"),
        textmateScope = "source.go",
        icon = go,
        title = "Go"
    ),
    PHP(
        extensions = listOf("php"),
        textmateScope = "source.php",
        icon = php,
        title = "PHP"
    ),
    RUST(
        extensions = listOf("rs"),
        textmateScope = "source.rust",
        icon = rust,
        title = "Rust"
    ),
    PASCAL(
        extensions = listOf("p", "pas"),
        textmateScope = "source.pascal",
        icon = null,
        title = "Pascal"
    ),
    ZIG(
        extensions = listOf("zig"),
        textmateScope = "source.zig",
        icon = zig,
        title = "Zig"
    ),
    NIM(
        extensions = listOf("nim"),
        textmateScope = "source.nim",
        icon = nim,
        title = "Nim"
    ),
    SWIFT(
        extensions = listOf("swift"),
        textmateScope = "source.swift",
        icon = swift,
        title = "Swift"
    ),
    DART(
        extensions = listOf("dart"),
        textmateScope = "source.dart",
        icon = dart,
        title = "Dart"
    ),
    ROCQ(
        extensions = listOf("v", "coq"),
        textmateScope = "source.coq",
        icon = null,
        title = "Rocq (Coq)"
    ),
    KOTLIN(
        extensions = listOf("kt", "kts"),
        textmateScope = "source.kotlin",
        icon = kotlin,
        title = "Kotlin"
    ),
    LISP(
        extensions = listOf("lisp", "clisp"),
        textmateScope = "source.lisp",
        icon = lisp,
        title = "Lisp"
    ),
    SHELL(
        extensions = listOf("sh", "bash", "bash_login", "bash_logout", "bash_profile", "bashrc", "profile", "rhistory", "rprofile", "zsh", "zlogin", "zlogout", "zprofile", "zshenv", "zshrc", "fish", "ksh"),
        textmateScope = "source.shell",
        icon = shell,
        title = "Shell script"
    ),
    WINDOWS_SHELL(
        extensions = listOf("cmd", "bat"),
        textmateScope = "source.batchfile",
        icon = shell,
        title = "Batch"
    ),
    POWERSHELL(
        extensions = listOf("ps1", "psm1", "psd1"),
        textmateScope = "source.powershell",
        icon = null,
        title = "PowerShell"
    ),
    SMALI(
        extensions = listOf("smali"),
        textmateScope = "source.smali",
        icon = null,
        title = "Smali"
    ),
    ASSEMBLY(
        extensions = listOf("asm"),
        textmateScope = "source.asm",
        icon = null,
        title = "Assembly"
    ),

    // Data Files
    SQL(
        extensions = listOf("sql", "dsql", "sqllite"),
        textmateScope = "source.sql",
        icon = sql,
        title = "SQL"
    ),
    TOML(
        extensions = listOf("toml"),
        textmateScope = "source.toml",
        icon = toml,
        title = "TOML"
    ),
    INI(
        extensions = listOf("ini"),
        textmateScope = "source.ini",
        icon = prop,
        title = "INI"
    ),
    PROPERTIES(
        extensions = listOf("properties", "cfg", "conf", "config", "editorconfig", "gitconfig", "gitmodules", "gitattributes"),
        textmateScope = "source.properties",
        icon = prop,
        iconOverride = mapOf(
            "gitmodules" to git,
            "gitattributes" to git,
            "gitconfig" to git,
        ),
        title = "Properties"
    ),
    IGNORE(
        extensions = listOf("gitignore", "gitignore_global", "gitkeep", "git-blame-ignore-revs"),
        textmateScope = "source.ignore",
        icon = git,
        title = "Ignore"
    ),

    // Documents
    TEXT(
        extensions = listOf("txt"),
        textmateScope = null,
        icon = text,
        title = "Plain text"
    ),
    LOG(
        extensions = listOf("log"),
        textmateScope = "text.log",
        icon = null,
        title = "Log"
    ),
    LATEX(
        extensions = listOf("latex", "tex", "ltx"),
        textmateScope = "text.tex.latex",
        icon = latex,
        title = "LaTeX"
    ),
    IMAGE(
        extensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "ico", "heic", "heif", "avif"),
        textmateScope = null,
        icon = image,
        title = "Image"
    ),
    AUDIO(
        extensions = listOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "wma", "opus"),
        textmateScope = null,
        icon = audio,
        title = "Audio"
    ),
    VIDEO(
        extensions = listOf("mp4", "avi", "mov", "mkv", "webm"),
        textmateScope = null,
        icon = video,
        title = "Video"
    ),
    ARCHIVE(
        extensions = listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xy"),
        textmateScope = null,
        icon = archive,
        title = "Archive"
    ),
    EXECUTABLE(
        extensions = listOf("exe", "dll", "so", "dylib", "bin"),
        textmateScope = null,
        icon = null,
        title = "Executable"
    ),
    APK(
        extensions = listOf("apk", "xapk", "apks"),
        textmateScope = null,
        icon = apk,
        title = "APK"
    ),
    UNKNOWN(
        extensions = emptyList(),
        textmateScope = null,
        icon = null,
        title = strings.unknown.getString()
    );

    companion object {
        fun fromExtension(ext: String): FileType {
            val normalized = ext.lowercase().removePrefix(".")
            return entries.firstOrNull { normalized in it.extensions } ?: UNKNOWN
        }

        fun knowsExtension(ext: String): Boolean {
            val normalized = ext.lowercase().removePrefix(".")
            return entries.any { normalized in it.extensions }
        }
    }
}