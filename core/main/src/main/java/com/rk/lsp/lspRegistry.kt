package com.rk.lsp

import com.rk.lsp.servers.Bash
import com.rk.lsp.servers.CSS
import com.rk.lsp.servers.HTML
import com.rk.lsp.servers.JSON
import com.rk.lsp.servers.Python
import com.rk.lsp.servers.TypeScript

val lspRegistry = listOf(
    Python(),
    HTML(),
    CSS(),
    TypeScript(),
    JSON(),
    Bash(),
)