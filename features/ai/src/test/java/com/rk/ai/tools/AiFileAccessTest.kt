package com.rk.ai.tools

import com.rk.file.FileWrapper
import com.rk.file.NetWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.net.URL

class AiFileAccessTest {
    @Test
    fun localFilesAreLocalAndExposeANativePath() {
        val local = FileWrapper(File("/sdcard/project/a.kt"))

        assertEquals(AiFileKind.Local, local.aiKind())
        assertNotNull(local.nativePathOrNull())
    }

    @Test
    fun remoteObjectsAreRemoteAndExposeNoNativePath() {
        val remote = NetWrapper(URL("https://example.com/readme.md"))

        assertEquals(AiFileKind.Remote, remote.aiKind())
        assertNull("a URL must never be handed to a shell", remote.nativePathOrNull())
    }
}
