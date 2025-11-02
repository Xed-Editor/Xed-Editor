package com.rk.file

import com.rk.resources.drawables

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

// TODO: Add icon for FileType.YAML
// TODO: Add icon for FileType.RUBY
// TODO: Add icon for FileType.SWIFT
// TODO: Add icon for FileType.DART
// TODO: Add icon for FileType.POWERSHELL
// TODO: Add icon for FileType.TOML
// TODO: Add icon for FileType.EXECUTABLE
// TODO: Add icon for FileType.GROOVY
// TODO: Add icon for FileType.PASCAL
// TODO: Add icon for FileType.ZIG
// TODO: Add icon for FileType.ASSEMBLY
// TODO: Add icon for FileType.SMALI
// TODO: Add icon for FileType.LATEX
// TODO: Add icon for FileType.LOG
// TODO: Add icon for FileType.NIM
// TODO: Add icon for FileType.COQ
// TODO: Add icon for gradle files

enum class FileType(val extensions: List<String>, val textmateScope: String?, val icon: Int?, val title: String) {
    // Web languages
    JAVASCRIPT(listOf("js", "mjs", "cjs", "jscsrc", "jshintrc", "mut"), "source.js", js, "JavaScript"),
    TYPESCRIPT(listOf("ts"), "source.ts", ts, "TypeScript"),
    JSX(listOf("jsx"), "source.js.jsx", react, "JavaScript JSX"),
    TSX(listOf("jsx"), "source.tsx", react, "TypeScript JSX"),
    HTML_BASIC(listOf("html", "htm", "xhtml", "xht"), "text.html.basic", html, "HTML"),
    HTMX(listOf("htmx"), "text.html.htmx", html, "HTMX"),
    CSS(listOf("css", "scss", "sass", "less"), "source.css", css, "CSS"),
    JSON(listOf("json", "jsonl", "jsonc"), "source.json", json, "JSON"),
    MARKDOWN(listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdtxt", "mdwn"), "text.html.markdown", markdown, "Markdown"),
    XML(listOf("xml", "xaml", "dtd", "plist", "ascx", "csproj", "wxi", "wxl", "wxs", "svg"), "text.xml", xml, "XML"),
    YAML(listOf("yaml", "yml", "eyaml", "eyml", "cff"), "source.yaml", null, "YAML"),

    // Programming Languages
    PYTHON(listOf("py", "pyi"), "source.python", python, "Python"),
    JAVA(listOf("java", "jav", "bsh"), "source.java", java, "Java"),
    GROOVY(listOf("gsh", "groovy", "gradle", "gvy", "gy"), "source.groovy", null, "Groovy"),
    C(listOf("c"), "source.c", c, "C"),
    CPP(listOf("cpp", "cxx", "cc", "c++", "h", "hpp", "hh", "hxx", "h++"), "source.cpp", cpp, "C++"),
    CSHARP(listOf("cs", "csx"), "source.cs", csharp, "C#"),
    RUBY(listOf("rb", "erb", "gemspec"), null, null, "Ruby"), // TODO: Add TextMate files
    LUA(listOf("lua"), "source.lua", lua, "Lua"),
    GO(listOf("go"), "source.go", go, "Go"),
    PHP(listOf("php"), "source.php", php, "PHP"),
    RUST(listOf("rs"), "source.rust", rust, "Rust"),
    PASCAL(listOf("p", "pas"), "source.pascal", null, "Pascal"),
    ZIG(listOf("zig"), "source.zig", null, "Zig"),
    NIM(listOf("nim"), "source.nim", null, "Nim"),
    SWIFT(listOf("swift"), null, null, "Swift"), // TODO: Add TextMate files
    DART(listOf("dart"), "source.dart", null, "Dart"),
    COQ(listOf("v", "coq"), "source.coq", null, "Coq"),
    KOTLIN(listOf("kt", "kts"), "source.kotlin", kotlin, "Kotlin"),
    LISP(listOf("lisp", "clisp"), "source.lisp", lisp, "Lisp"),
    SHELL(listOf("sh", "bash", "bash_login", "bash_logout", "bash_profile", "bashrc", "profile", "rhistory", "rprofile", "zsh", "zlogin", "zlogout", "zprofile", "zshenv", "zshrc", "fish", "ksh"), "source.shell", shell, "Shell script"),
    WINDOWS_SHELL(listOf("cmd", "bat"), "source.batchfile", shell, "Batch"),
    POWERSHELL(listOf("ps1", "psm1", "psd1"), null, null, "PowerShell"), // TODO: Add TextMate files
    SMALI(listOf("smali"),"source.smali", null, "Smali"),
    ASSEMBLY(listOf("asm"), "source.asm", null, "Assembly"),

    // Data Files
    SQL(listOf("sql"), "source.sql", sql, "SQL"),
    TOML(listOf("toml"), "source.toml", null, "TOML"),
    INI(listOf("ini", "cfg", "config", "editorconfig"), "source.ini", prop, "INI"),
    JAVA_PROPERTIES(listOf("properties"), "source.java-properties", prop, "Properties"),

    // Documents
    TEXT(listOf("txt"), null, text, "Plain text"),
    LOG(listOf("log"), null, null, "Log"), // TODO: Add TextMate files
    LATEX(listOf("latex", "tex", "ltx"), "text.tex.latex", null, "LaTeX"),
    IMAGE(listOf("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "ico", "heic", "heif", "avif"), null, image, "Image"),
    AUDIO(listOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "wma", "opus"), null, audio, "Audio"),
    VIDEO(listOf("mp4", "avi", "mov", "mkv", "webm"), null, video, "Video"),
    ARCHIVE(listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xy"), null, archive, "Archive"),
    EXECUTABLE(listOf("exe", "dll", "so", "dylib", "bin"), null, null, "Executable"),
    APK(listOf("apk", "xapk", "apks"), null, apk, "APK"),
    UNKNOWN(emptyList(), null, null, "Unknown");

    companion object {
        fun fromExtension(ext: String): FileType {
            val normalized = ext.lowercase().removePrefix(".")
            return entries.firstOrNull { normalized in it.extensions } ?: UNKNOWN
        }
    }
}