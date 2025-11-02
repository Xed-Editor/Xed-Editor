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
// TODO: Add icon for FileType.GRADLE
// TODO: Add icon for FileType.PASCAL
// TODO: Add icon for FileType.ZIG
// TODO: Add icon for FileType.ASSEMBLY
// TODO: Add icon for FileType.SMALI
// TODO: Add icon for FileType.LATEX
// TODO: Add icon for FileType.LOG
// TODO: Add icon for FileType.NIM
// TODO: Add icon for FileType.COQ

enum class FileType(val extensions: List<String>, val textmateScope: String?, val icon: Int?) {
    // Web languages
    JAVASCRIPT(listOf("js", "mjs", "cjs", "jscsrc", "jshintrc", "mut"), "source.js", js),
    TYPESCRIPT(listOf("ts"), "source.ts", ts),
    JSX(listOf("jsx"), "source.js.jsx", react),
    TSX(listOf("jsx"), "source.tsx", react),
    HTML_BASIC(listOf("html", "htm", "xhtml", "xht"), "text.html.basic", html),
    HTMX(listOf("htmx"), "text.html.htmx", html),
    CSS(listOf("css", "scss", "sass", "less"), "source.css", css),
    JSON(listOf("json"), "source.json", json),
    MARKDOWN(listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdtxt", "mdwn"), "text.html.markdown", markdown),
    XML(listOf("xml", "xaml", "dtd", "plist", "ascx", "csproj", "wxi", "wxl", "wxs", "svg"), "text.xml", xml),
    YAML(listOf("yaml", "yml", "eyaml", "eyml", "cff"), "source.yaml", null),

    // Programming Languages
    PYTHON(listOf("py", "pyi"), "source.python", python),
    JAVA(listOf("java", "jav", "bsh"), "source.java", java),
    GROOVY(listOf("gsh", "groovy", "gradle", "gvy", "gy"), "source.groovy", null),
    GRADLE(listOf("gradle"), "source.groovy", null),
    C(listOf("c"), "source.c", c),
    CPP(listOf("cpp", "cxx", "cc", "c++", "h", "hpp", "hh", "hxx", "h++"), "source.cpp", cpp),
    CSHARP(listOf("cs", "csx"), "source.cs", csharp),
    RUBY(listOf("rb", "erb", "gemspec"), null, null), // TODO: Add TextMate files
    LUA(listOf("lua"), "source.lua", lua),
    GO(listOf("go"), "source.go", go),
    PHP(listOf("php"), "source.php", php),
    RUST(listOf("rs"), "source.rust", rust),
    PASCAL(listOf("p", "pas"), "source.pascal", null),
    ZIG(listOf("zig"), "source.zig", null),
    NIM(listOf("nim"), "source.nim", null),
    SWIFT(listOf("swift"), null, null), // TODO: Add TextMate files
    DART(listOf("dart"), "source.dart", null),
    COQ(listOf("v", "coq"), "source.coq", null),
    KOTLIN(listOf("kt", "kts"), "source.kotlin", kotlin),
    LISP(listOf("lisp", "clisp"), "source.lisp", lisp),
    SHELL(listOf("sh", "bash", "bash_login", "bash_logout", "bash_profile", "bashrc", "profile", "rhistory", "rprofile", "zsh", "zlogin", "zlogout", "zprofile", "zshenv", "zshrc", "fish", "ksh"), "source.shell", shell),
    WINDOWS_SHELL(listOf("cmd", "bat"), "source.batchfile", shell),
    POWERSHELL(listOf("ps1", "psm1", "psd1"), null, null), // TODO: Add TextMate files
    SMALI(listOf("smali"),"source.smali", null),
    ASSEMBLY(listOf("asm"), "source.asm", null),

    // Data Files
    SQL(listOf("sql"), "source.sql", sql),
    TOML(listOf("toml"), "source.toml", null),
    INI(listOf("ini", "cfg", "config", "editorconfig"), "source.ini", prop),
    JAVA_PROPERTIES(listOf("properties"), "source.java-properties", prop),
    PRO(listOf("pro"), "source.shell", prop),

    // Documents
    TEXT(listOf("txt"), null, text),
    LOG(listOf("log"), null, null), // TODO: Add TextMate files
    LATEX(listOf("latex", "tex", "ltx"), "text.tex.latex", null),
    IMAGE(listOf("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "ico", "heic", "heif", "avif"), null, image),
    AUDIO(listOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "wma", "opus"), null, audio),
    VIDEO(listOf("mp4", "avi", "mov", "mkv", "webm"), null, video),
    ARCHIVE(listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xy"), null, archive),
    EXECUTABLE(listOf("exe", "dll", "so", "dylib", "bin"), null, null),
    APK(listOf("apk", "xapk", "apks"), null, apk),
    UNKNOWN(emptyList(), null, null);

    companion object {
        fun fromExtension(ext: String): FileType {
            val normalized = ext.lowercase().removePrefix(".")
            return entries.firstOrNull { normalized in it.extensions } ?: UNKNOWN
        }
    }
}