package com.rk.ai.bridge

import com.google.gson.JsonObject

class AnnotatedTool(
    private val delegate: McpTool,
    private val annotations: ToolAnnotations,
) : McpTool by delegate {
    override fun getAnnotations(): ToolAnnotations = annotations
}
