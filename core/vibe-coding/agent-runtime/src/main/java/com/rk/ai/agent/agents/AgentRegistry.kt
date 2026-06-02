@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent.agents

import android.content.Context
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.models.InputSchema
import com.rk.ai.service.IdeService
import com.google.gson.JsonParser
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class SubAgentRegistration(
    val agent: SubAgent,
    val enabled: Boolean = true,
)

class AgentRegistry(
    private val context: Context,
    private val ideService: IdeService,
) {
    private val agents = mutableMapOf<String, SubAgentRegistration>()

    init {
        register(CodeReviewAgent(ideService))
        register(BugHunterAgent(ideService))
        register(ArchitectureAgent(ideService))
        register(TestGenerationAgent(ideService))
    }

    fun register(agent: SubAgent, enabled: Boolean = true) {
        agents[agent.name] = SubAgentRegistration(agent, enabled)
    }

    fun get(name: String): SubAgent? = agents[name]?.takeIf { it.enabled }?.agent

    fun getAllEnabled(): List<SubAgent> = agents.values.filter { it.enabled }.map { it.agent }

    fun getAgentListTool(): Tool {
        val agentsInfo = getAllEnabled().joinToString("\n") { agent ->
            val caps = agent.capabilities.joinToString(", ") { it.name }
            "  - ${agent.name}: ${agent.description}\n    Capabilities: $caps"
        }

        return Tool(
            name = "listAgents",
            description = "Lists all available sub-agents and their capabilities. Sub-agents are specialized assistants that can perform specific tasks like code review, architecture design, bug hunting, and test generation.",
            execute = { _ ->
                val text = buildString {
                    appendLine("Available sub-agents:")
                    appendLine(agentsInfo.ifEmpty { "  No agents registered" })
                    appendLine()
                    appendLine("Use 'delegateTask' to assign a task to a specific agent.")
                }
                listOf(UIMessagePart.Text(text))
            },
        )
    }

    fun getDelegateTool(onResult: suspend (String, AgentResult) -> Unit): Tool = Tool(
        name = "delegateTask",
        description = "Delegates a task to a specialized sub-agent for parallel execution. Use this for code review, architecture analysis, bug hunting, or test generation while you continue other work.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("agentName") { put("type", "string"); put("description", "Name of the sub-agent to delegate to (use listAgents to see available agents)") }
                    putJsonObject("task") { put("type", "string"); put("description", "Detailed description of the task to perform") }
                    putJsonObject("context") { put("type", "string"); put("description", "Additional context such as file paths, code snippets, or requirements") }
                },
                required = listOf("agentName", "task"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val agentName = obj["agentName"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing agentName"))
            val taskDesc = obj["task"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing task"))
            val contextStr = obj["context"]?.asJsonPrimitive?.asString ?: ""

            val agent = get(agentName)
            if (agent == null) {
                return@Tool listOf(UIMessagePart.Text("Agent '$agentName' not found or disabled. Use listAgents to see available agents."))
            }

            val task = AgentTask(
                id = Uuid.random().toString(),
                prompt = taskDesc,
                contextMessages = listOf(contextStr).filter { it.isNotBlank() },
                maxSteps = 20,
            )

            kotlinx.coroutines.runBlocking {
                val result = agent.execute(task)
                onResult(agentName, result)
            }

            listOf(UIMessagePart.Text("Task delegated to '$agentName'. Check the agent activity panel for results."))
        },
    )
}
