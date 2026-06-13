@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent.agents

import android.content.Context
import com.rk.ai.agent.files.AgentFileLoader
import com.rk.ai.agent.files.FileAgentDefinition
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.models.InputSchema
import com.rk.ai.providers.ProviderManager
import com.rk.ai.persistence.settings.SettingsStore
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

data class FileBasedSubAgent(
    val definition: FileAgentDefinition,
) : SubAgent {
    override val name: String get() = definition.name.lowercase().replace(" ", "-")
    override val description: String get() = definition.description
    override val capabilities: List<AgentCapability> get() = listOf(
        AgentCapability(
            name = name,
            description = description,
            inputSchema = "task: string, context: string",
        )
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return AgentResult.Success(
            output = buildString {
                appendLine("## Agent: ${definition.name}")
                appendLine()
                appendLine("Task: ${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("Context:")
                    task.contextMessages.forEach { appendLine(it) }
                }
                appendLine()
                appendLine("## Instructions")
                appendLine(definition.prompt)
            },
            summary = "File-based agent '${definition.name}' prepared for task",
        )
    }
}

class AgentRegistry(
    private val context: Context,
    private val ideService: IdeService,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
) {
    private val agents = mutableMapOf<String, SubAgentRegistration>()

    init {
        register(CodeReviewAgent(ideService, providerManager, settingsStore))
        register(BugHunterAgent(ideService, providerManager, settingsStore))
        register(ArchitectureAgent(ideService, providerManager, settingsStore))
        register(TestGenerationAgent(ideService, providerManager, settingsStore))
        loadFileBasedAgents()
    }

    fun loadFileBasedAgents() {
        val fileAgents = AgentFileLoader.listAgents(context)
        for (agentDef in fileAgents) {
            if (agentDef.hidden) continue
            val agent = FileBasedSubAgent(agentDef)
            if (!agents.containsKey(agent.name)) {
                agents[agent.name] = SubAgentRegistration(agent, true)
            }
        }
    }

    fun reloadFileBasedAgents() {
        agents.keys.removeAll { agents[it]?.agent is FileBasedSubAgent }
        loadFileBasedAgents()
    }

    fun register(agent: SubAgent, enabled: Boolean = true) {
        agents[agent.name] = SubAgentRegistration(agent, enabled)
    }

    fun get(name: String): SubAgent? = agents[name]?.takeIf { it.enabled }?.agent

    fun getAllEnabled(): List<SubAgent> = agents.values.filter { it.enabled }.map { it.agent }

    fun getAgentListTool(): Tool {
        val builtinAgents = agents.values.filter { it.enabled && it.agent !is FileBasedSubAgent }.map { it.agent }
        val fileAgents = agents.values.filter { it.enabled && it.agent is FileBasedSubAgent }.map { it.agent as FileBasedSubAgent }

        val agentsInfo = buildString {
            if (builtinAgents.isNotEmpty()) {
                appendLine("Built-in agents:")
                builtinAgents.forEach { agent ->
                    val caps = agent.capabilities.joinToString(", ") { it.name }
                    appendLine("  - ${agent.name}: ${agent.description}")
                    appendLine("    Capabilities: $caps")
                }
                appendLine()
            }
            if (fileAgents.isNotEmpty()) {
                appendLine("Custom agents (from .xed/agents/):")
                fileAgents.forEach { agent ->
                    appendLine("  - ${agent.name}: ${agent.description}")
                }
            }
        }

        return Tool(
            name = "listAgents",
            description = "Lists all available sub-agents and their capabilities. Includes both built-in agents and custom agents from .xed/agents/. Sub-agents are specialized assistants that can perform specific tasks like code review, architecture design, bug hunting, and test generation.",
            execute = { _ ->
                val text = buildString {
                    appendLine("Available sub-agents:")
                    appendLine(agentsInfo.ifEmpty { "  No agents registered" })
                    appendLine()
                    appendLine("Use 'delegateTask' to assign a task to a specific agent.")
                    if (fileAgents.isNotEmpty()) {
                        appendLine("Add custom agents by creating .md files in .xed/agents/")
                    }
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

            val result = agent.execute(task)
            onResult(agentName, result)

            listOf(UIMessagePart.Text("Task delegated to '$agentName'. Check the agent activity panel for results."))
        },
    )
}
