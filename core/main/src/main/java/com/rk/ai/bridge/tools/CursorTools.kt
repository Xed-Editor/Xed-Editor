package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetSymbolUnderCursorTool : BaseMcpTool() {
    override fun getName(): String = "getSymbolUnderCursor"
    override fun getDescription(): String = "Gets the symbol under the cursor in the active editor."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val result = ideService.getSymbolUnderCursor()
        return jsonResult(result)
    }
}
