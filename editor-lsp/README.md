## About

**Experimental, work in progress**

`editor-lsp` module is a language service protocol client based on lsp4j, providing language service
protocol support for sora-editor.

Based on the module, you can access different language servers to provide features for different
languages, such as auto-completion, formatting, etc.

## Features(already available)

1.`textDocument/formatting`

2.`textDocument/rangeFormatting`

3.`textDocument/diagnostic`

## How to connect to the language server

We recommend to use socket to connect to the language server, you can let the language server run on
another process without worrying that a crash of the language server will cause the main process to
crash.

See [this](https://github.com/Rosemoe/sora-editor/blob/main/app/src/main/java/io/github/rosemoe/sora/app/LspTestActivity.kt#L135)
to see example of connecting to a language server

