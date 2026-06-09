package com.rk.ai.agent.tools

import android.util.Log
import com.google.gson.JsonElement
import com.rk.ai.agent.VibeCodingError
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class ToolValidator {
    private val tag = "ToolValidator"

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
    ) {
        companion object {
            val valid = ValidationResult(true)
            fun error(msg: String) = ValidationResult(false, errors = listOf(msg))
            fun errors(msgs: List<String>) = ValidationResult(false, errors = msgs)
        }
    }

    fun validate(tool: Tool, args: JsonElement): ValidationResult {
        val schema = tool.parameters()
            ?: return ValidationResult.valid

        if (schema !is InputSchema.Obj) return ValidationResult.valid

        val requiredFields = schema.required ?: emptyList()
        val properties = schema.properties
        val errors = mutableListOf<String>()

        val argsObj = if (args.isJsonObject) args.getAsJsonObject() else {
            return ValidationResult.error("Arguments must be a JSON object, got ${args::class.simpleName}")
        }

        for (field in requiredFields) {
            if (!argsObj.has(field) || argsObj.get(field).isJsonNull) {
                errors.add("Missing required field '$field'")
            }
        }

        for ((key, value) in properties) {
            val fieldSchema = value.jsonObject
            val typeElement = fieldSchema["type"]
            val expectedType = typeElement?.jsonPrimitive?.contentOrNull
            val actual = argsObj.get(key) ?: continue

            if (actual.isJsonNull) continue

            when (expectedType) {
                "string" -> if (!actual.isJsonPrimitive || !actual.getAsJsonPrimitive().isString) {
                    errors.add("Field '$key' must be a string")
                }
                "integer", "number" -> if (!actual.isJsonPrimitive || !actual.getAsJsonPrimitive().isNumber) {
                    errors.add("Field '$key' must be a number")
                }
                "boolean" -> if (!actual.isJsonPrimitive || !actual.getAsJsonPrimitive().isBoolean) {
                    errors.add("Field '$key' must be a boolean")
                }
                "array" -> if (!actual.isJsonArray) {
                    errors.add("Field '$key' must be an array")
                }
                "object" -> if (!actual.isJsonObject) {
                    errors.add("Field '$key' must be an object")
                }
            }
        }

        if (errors.isNotEmpty()) {
            Log.w(tag, "Validation failed for tool '${tool.name}': ${errors.joinToString("; ")}")
        }
        return if (errors.isEmpty()) ValidationResult.valid
        else ValidationResult(false, errors = errors)
    }

    fun validateAndThrow(tool: Tool, args: JsonElement) {
        val result = validate(tool, args)
        if (!result.isValid) {
            val error = VibeCodingError.ToolError.InvalidArgs(
                toolName = tool.name,
                validationErrors = result.errors,
            )
            throw RuntimeException(error.toString())
        }
    }

    fun validateWithSchema(toolName: String, schema: InputSchema?, args: JsonElement) {
        if (schema == null) return
        val tool = Tool(name = toolName, description = "", parameters = { schema })
        val result = validate(tool, args)
        if (!result.isValid) {
            throw RuntimeException(
                VibeCodingError.ToolError.InvalidArgs(
                    toolName = toolName,
                    validationErrors = result.errors,
                ).toString()
            )
        }
    }

    companion object {
        val instance = ToolValidator()
    }
}
