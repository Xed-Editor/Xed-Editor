package com.rk.tabs.markdown

/**
 * Wraps converted Markdown HTML in a full document styled to closely match GitHub's rendered
 * Markdown (the "github-markdown-css" look), with both light and dark palettes. The stylesheet is
 * bundled inline so the preview is fully offline — no CDN, no network.
 */
object GithubMarkdownStyle {

    fun document(bodyHtml: String, dark: Boolean): String {
        val theme = if (dark) "dark" else "light"
        return """
            <!DOCTYPE html>
            <html lang="en" data-theme="$theme">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
            <style>$CSS</style>
            </head>
            <body><article class="markdown-body">
            $bodyHtml
            </article></body>
            </html>
        """
            .trimIndent()
    }

    // Palette + layout distilled from GitHub's markdown stylesheet (light + dark).
    private val CSS =
        """
        :root {
          --fg: #1f2328; --muted: #59636e; --bg: #ffffff;
          --border: #d1d9e0; --border-muted: #d1d9e0b3;
          --link: #0969da; --code-bg: #818b981f; --canvas-subtle: #f6f8fa;
          --quote-fg: #59636e; --quote-border: #d1d9e0;
        }
        html[data-theme="dark"] {
          --fg: #e6edf3; --muted: #9198a1; --bg: #0d1117;
          --border: #3d444d; --border-muted: #3d444db3;
          --link: #4493f8; --code-bg: #656c7633; --canvas-subtle: #151b23;
          --quote-fg: #9198a1; --quote-border: #3d444d;
        }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; background: var(--bg); }
        .markdown-body {
          color: var(--fg);
          background: var(--bg);
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif;
          font-size: 16px;
          line-height: 1.5;
          word-wrap: break-word;
          padding: 16px;
          max-width: 980px;
          margin: 0 auto;
        }
        .markdown-body > *:first-child { margin-top: 0 !important; }
        .markdown-body > *:last-child { margin-bottom: 0 !important; }
        .markdown-body h1, .markdown-body h2, .markdown-body h3,
        .markdown-body h4, .markdown-body h5, .markdown-body h6 {
          margin-top: 24px; margin-bottom: 16px; font-weight: 600; line-height: 1.25;
        }
        .markdown-body h1 { font-size: 2em; padding-bottom: .3em; border-bottom: 1px solid var(--border-muted); }
        .markdown-body h2 { font-size: 1.5em; padding-bottom: .3em; border-bottom: 1px solid var(--border-muted); }
        .markdown-body h3 { font-size: 1.25em; }
        .markdown-body h4 { font-size: 1em; }
        .markdown-body h5 { font-size: .875em; }
        .markdown-body h6 { font-size: .85em; color: var(--muted); }
        .markdown-body p { margin-top: 0; margin-bottom: 16px; }
        .markdown-body a { color: var(--link); text-decoration: none; }
        .markdown-body a:hover { text-decoration: underline; }
        .markdown-body strong { font-weight: 600; }
        .markdown-body img { max-width: 100%; box-sizing: content-box; background: transparent; }
        .markdown-body code {
          font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace;
          font-size: 85%;
          padding: .2em .4em;
          margin: 0;
          background: var(--code-bg);
          border-radius: 6px;
          white-space: break-spaces;
        }
        .markdown-body pre {
          padding: 16px;
          overflow: auto;
          font-size: 85%;
          line-height: 1.45;
          background: var(--canvas-subtle);
          border-radius: 6px;
          margin-top: 0;
          margin-bottom: 16px;
        }
        .markdown-body pre code {
          padding: 0; margin: 0; background: transparent; border: 0; font-size: 100%; white-space: pre;
        }
        .markdown-body blockquote {
          margin: 0 0 16px 0;
          padding: 0 1em;
          color: var(--quote-fg);
          border-left: .25em solid var(--quote-border);
        }
        .markdown-body blockquote > :last-child { margin-bottom: 0; }
        .markdown-body ul, .markdown-body ol { margin-top: 0; margin-bottom: 16px; padding-left: 2em; }
        .markdown-body li { margin-top: .25em; }
        .markdown-body li > ul, .markdown-body li > ol { margin-top: .25em; margin-bottom: 0; }
        .markdown-body ul.contains-task-list, .markdown-body li.task-list-item { list-style-type: none; }
        .markdown-body input[type="checkbox"] { margin: 0 .2em .25em -1.4em; vertical-align: middle; }
        .markdown-body hr {
          height: .25em; padding: 0; margin: 24px 0; background: var(--border); border: 0;
        }
        .markdown-body table {
          border-collapse: collapse; margin-top: 0; margin-bottom: 16px; display: block; width: max-content;
          max-width: 100%; overflow: auto;
        }
        .markdown-body table th, .markdown-body table td {
          padding: 6px 13px; border: 1px solid var(--border);
        }
        .markdown-body table th { font-weight: 600; background: var(--canvas-subtle); }
        .markdown-body table tr { background: var(--bg); border-top: 1px solid var(--border-muted); }
        .markdown-body table tr:nth-child(2n) { background: var(--canvas-subtle); }
        .markdown-body del { text-decoration: line-through; }
        """
            .trimIndent()
}
