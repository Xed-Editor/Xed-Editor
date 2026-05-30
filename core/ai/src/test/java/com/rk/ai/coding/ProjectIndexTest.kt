package com.rk.ai.coding

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.coding.fakes.FakeIdeService
import com.rk.ai.coding.index.ProjectIndex
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIndexTest {
    @Test
    fun findsClassesAndFunctionsThroughProjectOps() = runBlocking {
        val fake = FakeIdeService(File("/tmp/native-index"))
        fake.putFile("src/A.kt", "class TargetClass\nfun targetFunction() = Unit")
        val index = ProjectIndex(fake, fake)

        val classes = index.findClass("TargetClass")
        val functions = index.findFunction("targetFunction")

        assertEquals(1, classes.size)
        assertTrue(classes.first().snippet.contains("class TargetClass"))
        assertEquals(1, functions.size)
        assertTrue(functions.first().snippet.contains("fun targetFunction"))
    }

    @Test
    fun findsReferencesThroughLspOps() = runBlocking {
        val fake = FakeIdeService(File("/tmp/native-refs"))
        fake.references = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("uri", "file:///tmp/native-refs/src/A.kt")
                add("range", JsonObject().apply {
                    add("start", JsonObject().apply {
                        addProperty("line", 3)
                        addProperty("character", 7)
                    })
                    add("end", JsonObject().apply {
                        addProperty("line", 3)
                        addProperty("character", 13)
                    })
                })
            })
        }
        val refs = ProjectIndex(fake, fake).findReferences("src/A.kt", 1, 1)

        assertEquals(1, refs.size)
        assertEquals(3, refs.first().startLine)
        assertEquals(7, refs.first().startColumn)
    }
}
