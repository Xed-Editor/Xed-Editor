package com.rk.ai.tab

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.activities.main.session.PayloadTabRegistry
import com.rk.activities.main.session.PayloadTabState
import com.rk.ai.chat.AiChatController
import com.rk.ai.chat.AiChatScreen
import com.rk.ai.chat.AiChatSnapshot
import com.rk.ai.chat.decodeChatSnapshot
import com.rk.ai.icons.SparklesBig
import com.rk.tabs.base.Tab

class AiTab(private val restored: AiChatSnapshot? = null) : Tab() {
    override val name: String = "AI"
    override val icon: ImageVector = SparklesBig
    override val title: String = "AI"

    private val controller: AiChatController = AiChatController()
        .also { if (restored != null) it.applySnapshot(restored) }

    @Composable
    override fun Content() {
        AiChatScreen(controller = controller)
    }

    @Composable
    override fun RowScope.Actions() {
        AiTasksButton(controller = controller)
    }

    /** Hands this chat to the session as an opaque payload the AI feature knows how to read back. */
    override fun getState(): PayloadTabState =
        PayloadTabState(
            type = TYPE,
            payload = controller.persist(),
            projectRoot = projectRoot,
            scopeRoot = scopeRoot,
        )

    override fun onTabRemoved() {
        controller.dispose()
    }

    companion object {
        /** Session key for an AI chat; stable, because a restored session looks it up by this. */
        const val TYPE = "ai_chat"

        /**
         * Registers the restore path for [AiTab]. The type string is the same one [getState] writes,
         * so a session saved by one install restores in another; the payload carries its own version,
         * and [decodeChatSnapshot] returns null (an empty chat) rather than throwing when it meets
         * one it cannot read.
         */
        fun register() {
            PayloadTabRegistry.register(TYPE) { payload -> AiTab(restored = decodeChatSnapshot(payload)) }
        }

        fun unregister() {
            PayloadTabRegistry.unregister(TYPE)
        }
    }
}
