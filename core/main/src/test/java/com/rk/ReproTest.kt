package com.rk

import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.lsp.events.AsyncEventListener
import io.github.rosemoe.sora.lsp.events.EventContext
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.document.documentSave
import io.github.rosemoe.sora.lsp.utils.FileUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class CoroutineReproTest {

    // Update Kotlin version in D:/android/Xed-Editor/soraX/gradle/libs.versions.toml
    // to make the test pass without ClassCastException
    @Test
    fun myRepro() {
        val lspProject = LspProject("test")
        val lspEditor = LspEditor(lspProject, FileUri("test"))

        // Add a listener that suspends
        lspProject.eventEmitter.addListener(
            object : AsyncEventListener() {
                override val eventName = EventType.documentSave

                override suspend fun doHandleAsync(context: EventContext) {
                    delay(10.milliseconds) // Force suspension
                }
            }
        )

        runBlocking {
            withContext(Dispatchers.IO) {
                runCatching {
                    // Using a safe call operator
                    val e: LspEditor? = lspEditor
                    e?.saveDocument()
                }
                    .onFailure {
                        fail("Reproduced crash: $it")
                        it.printStackTrace()
                    }
            }
        }
    }
}
