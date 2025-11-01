package com.rk.lsp

import com.rk.lsp.servers.Bash
import com.rk.lsp.servers.CSS
import com.rk.lsp.servers.HTML
import com.rk.lsp.servers.JSON
import com.rk.lsp.servers.LUA
import com.rk.lsp.servers.Python
import com.rk.lsp.servers.TypeScript
import com.rk.lsp.servers.XML

val lspRegistry = listOf(
    Python(),
    HTML(),
    CSS(),
    TypeScript(),
    JSON(),
    Bash(),
    XML(),
    LUA()
)