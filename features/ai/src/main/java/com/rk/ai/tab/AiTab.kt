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
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.base.Tab

class AiTab(private val restored: AiChatSnapshot? = null) : Tab() {
    override val name: String = strings.ai_feature_label.getString()
    override val icon: ImageVector = SparklesBig
    override val title: String = strings.ai_feature_label.getString()

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

        fun register() {
            PayloadTabRegistry.register(TYPE) { payload -> AiTab(restored = decodeChatSnapshot(payload)) }
        }

        fun unregister() {
            PayloadTabRegistry.unregister(TYPE)
        }
    }
}
