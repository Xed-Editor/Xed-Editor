package com.rk.ai.tools

import com.rk.ai.model.AiTodo
import com.rk.ai.model.TodoStatus
import com.rk.ai.model.parseTodos
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallViewTest {
    @Test
    fun singleArgumentIsPromotedToTheHeader() {
        val view = toolCallView("read_file", """{"path":"app/src/Main.kt"}""")

        assertEquals("Read file", view.action)
        assertEquals("app/src/Main.kt", view.target)
        assertTrue("nothing left for the body", view.arguments.isEmpty())
    }

    @Test
    fun lineRangeBecomesAReadableField() {
        val view = toolCallView("read_file", """{"path":"a.kt","start_line":"10","end_line":"40"}""")

        assertEquals(listOf<ToolBodyPart>(ToolBodyPart.Field("Lines", "10–40")), view.arguments)
    }

    @Test
    fun fileContentIsShownAsABlockRatherThanRawJson() {
        val view = toolCallView("write_file", """{"path":"a.kt","content":"fun main() {}"}""")

        assertEquals("a.kt", view.target)
        assertEquals(listOf<ToolBodyPart>(ToolBodyPart.Block("Content", "fun main() {}")), view.arguments)
    }

    @Test
    fun editShowsFindAndReplaceSeparately() {
        val view =
            toolCallView(
                "edit_file",
                """{"path":"a.kt","old_string":"one","new_string":"two","replace_all":"true"}""",
            )

        assertEquals(
            listOf(
                ToolBodyPart.Block("Find", "one"),
                ToolBodyPart.Block("Replace with", "two"),
                ToolBodyPart.Field("Replace all", "yes"),
            ),
            view.arguments,
        )
    }

    @Test
    fun copyShowsBothPathsInTheHeader() {
        val view = toolCallView("copy_path", """{"from":"a.txt","to":"b.txt"}""")

        assertEquals("a.txt → b.txt", view.target)
        assertTrue(view.arguments.isEmpty())
    }

    @Test
    fun shellArgumentsStayOutOfRawJson() {
        val view = toolCallView("run_ubuntu", """{"command":"ls -la","working_dir":"/root","timeout_seconds":"120"}""")

        assertEquals("Run in Ubuntu", view.action)
        assertEquals("ls -la", view.target)
        assertEquals(
            listOf(
                ToolBodyPart.Field("Working dir", "/root"),
                ToolBodyPart.Field("Timeout", "120s"),
            ),
            view.arguments,
        )
    }

    @Test
    fun longShellCommandIsRepeatedInTheBody() {
        val command = "echo " + "x".repeat(200)
        val view = toolCallView("run_android_shell", """{"command":"$command"}""")

        assertEquals(listOf<ToolBodyPart>(ToolBodyPart.Block("Command", command)), view.arguments)
    }

    @Test
    fun subAgentShowsItsDescriptionAndTask() {
        val view = toolCallView("spawn_agent", """{"description":"Find the auth code","prompt":"Locate the login flow"}""")

        assertEquals("Sub-agent", view.action)
        assertEquals("Find the auth code", view.target)
        assertEquals(listOf<ToolBodyPart>(ToolBodyPart.Field("Task", "Locate the login flow")), view.arguments)
    }

    @Test
    fun askUserListsItsOptions() {
        val view = toolCallView("ask_user", """{"question":"Which database?","options":["Postgres","SQLite"]}""")

        assertEquals("Ask you", view.action)
        assertEquals("Which database?", view.target)
        assertEquals(
            listOf(
                ToolBodyPart.Field("Option 1", "Postgres"),
                ToolBodyPart.Field("Option 2", "SQLite"),
            ),
            view.arguments,
        )
    }

    @Test
    fun setGoalShowsTheGoalInTheHeader() {
        val view = toolCallView("set_goal", """{"goal":"Ship the parser"}""")

        assertEquals("Set goal", view.action)
        assertEquals("Ship the parser", view.target)
    }

    @Test
    fun clearingTheGoalIsLabelledAsSuch() {
        val view = toolCallView("set_goal", """{"goal":""}""")

        assertEquals("Clear goal", view.action)
        assertEquals(null, view.target)
    }

    @Test
    fun writeTodosRendersEachTaskWithItsState() {
        val view =
            toolCallView(
                "write_todos",
                """
                {"todos":[
                  {"content":"Write the parser","status":"completed"},
                  {"content":"Wire the UI","status":"in_progress"}
                ]}
                """
                    .trimIndent(),
            )

        assertEquals("Update tasks", view.action)
        assertEquals("2 tasks", view.target)
        assertEquals(
            listOf(
                ToolBodyPart.Field("Done", "Write the parser"),
                ToolBodyPart.Field("Doing", "Wire the UI"),
            ),
            view.arguments,
        )
    }

    @Test
    fun unknownTodoStatusFallsBackToPending() {
        val todos = parseTodos(Json.parseToJsonElement("""[{"content":"x","status":"???"}]"""))

        assertEquals(listOf(AiTodo("x", TodoStatus.Pending)), todos)
    }

    @Test
    fun malformedTodoEntriesAreSkipped() {
        val todos = parseTodos(Json.parseToJsonElement("""[{"status":"pending"},"nope"]"""))

        assertTrue(todos.isEmpty())
    }

    @Test
    fun unknownToolFallsBackToKeyValueRows() {
        val view = toolCallView("some_new_tool", """{"alpha":"one","beta":"two"}""")

        assertEquals("Some new tool", view.action)
        assertEquals("one", view.target)
        assertEquals(
            listOf(
                ToolBodyPart.Field("Alpha", "one"),
                ToolBodyPart.Field("Beta", "two"),
            ),
            view.arguments,
        )
    }

    @Test
    fun extensionToolPresenterIsUsedWhenRegistered() {
        val tool =
            aiTool("some_new_tool", "A tool from an extension.", AiToolKind.Read) {
                stringParam("alpha", "First value")
                presents { args -> ToolCallView("Custom action", args.displayArg("alpha"), emptyList()) }
                executes { "ok" }
            }
        AiToolRegistry.registerTool(tool)

        try {
            val view = toolCallView("some_new_tool", """{"alpha":"one"}""")

            assertEquals("Custom action", view.action)
            assertEquals("one", view.target)
            assertTrue(view.arguments.isEmpty())
        } finally {
            AiToolRegistry.unregisterTool(tool)
        }
    }

    @Test
    fun malformedArgumentsDoNotCrash() {
        val view = toolCallView("read_file", "not json at all")

        assertEquals("Read file", view.action)
        assertEquals(null, view.target)
        assertTrue(view.arguments.isEmpty())
    }

    @Test
    fun missingArgumentsDoNotCrash() {
        val view = toolCallView("read_file", null)

        assertEquals("Read file", view.action)
        assertEquals(null, view.target)
        assertTrue(view.arguments.isEmpty())
    }
}
