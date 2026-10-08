package com.aiagent.ai_voice_agent.engine.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool parameter definition
 */
data class ToolParam(
    val name: String,
    val type: String, // string, number, boolean, object, array
    val description: String,
    val required: Boolean = false,
    val enumValues: List<String>? = null
) {
    fun toJson(): JSONObject {
        val schema = JSONObject().apply {
            put("type", type)
            put("description", description)
            enumValues?.let { put("enum", JSONArray(it)) }
        }
        return schema
    }
}

/**
 * Tool execution result
 */
data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?>? = null
) {
    companion object {
        fun success(message: String, data: Map<String, Any?>? = null) =
            ToolResult(success = true, message = message, data = data)

        fun failure(message: String) =
            ToolResult(success = false, message = message)
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("success", success)
            put("message", message)
            data?.let { put("data", JSONObject(it)) }
        }
    }

    fun toJsonString(): String = toJson().toString()
}

/**
 * Base tool interface — all agent tools implement this
 */
interface AgentTool {
    val name: String
    val description: String
    val parameters: List<ToolParam>

    /**
     * Execute the tool with given parameters
     */
    suspend fun execute(params: Map<String, Any?>): ToolResult

    /**
     * Convert to OpenAI function calling schema
     */
    fun toFunctionSchema(): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()

        for (param in parameters) {
            properties.put(param.name, param.toJson())
            if (param.required) {
                required.put(param.name)
            }
        }

        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", properties)
                    put("required", required)
                })
            })
        }
    }
}
