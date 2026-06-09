package com.rk.ai.agent.tools

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.rk.ai.agent.VibeCodingError
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import kotlinx.serialization.json.JsonElement as KxJsonElement
import kotlinx.serialization.json.JsonObject as KxJsonObject
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

        val argsObj = try {
            args.asJsonObject
        } catch (e: Exception) {
            return ValidationResult.error("Arguments must be a JSON object, got ${args::class.simpleName}")
        }

        for (field in requiredFields) {
            if (!argsObj.has(field) || argsObj.get(field).isJsonNull) {
                errors.add("Missing required field '$field'")
            }
        }

        for ((key, value) in properties.entries) {
            val fieldSchema = value.asJsonObject
            val expectedType = fieldSchema["type"]?.asString
            val actual = argsObj.get(key) ?: continue

            if (actual.isJsonNull) continue

            when (expectedType) {
                "string" -> if (!actual.isJsonPrimitive || !actual.asJsonPrimitive.isString) {
                    errors.add("Field '$key' must be a string, got ${actual.asJsonPrimitive?.let { if (it.isString) "string" else "number" } ?: actual::class.simpleName}")
                }
                "integer", "number" -> if (!actual.isJsonPrimitive || !actual.asJsonPrimitive.isNumber) {
                    errors.add("Field '$key' must be a number, got ${actual.asJsonPrimitive?.let { "primitive" } ?: actual::class.simpleName}")
                }
                "boolean" -> if (!actual.isJsonPrimitive || !actual.asJsonPrimitive.isBoolean) {
                    errors.add("Field '$key' must be a boolean, got ${actual.asJsonPrimitive?.let { "primitive" } ?: actual::class.simpleName}")
                }
                "array" -> if (!actual.isJsonArray) {
                    errors.add("Field '$key' must be an array, got ${actual::class.simpleName}")
                }
                "object" -> if (!actual.isJsonObject) {
                    errors.add("Field '$key' must be an object, got ${actual::class.simpleName}")
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
            throw VibeCodingError.ToolError.InvalidArgs(
                toolName = tool.name,
                validationErrors = result.errors,
            )
        }
    }

    companion object {
        val instance = ToolValidator()
    }
}
