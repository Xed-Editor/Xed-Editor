package com.rk.ai.tab

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.ai.chat.AiChatController
import com.rk.ai.chat.AiChatScreen
import com.rk.ai.icons.AiBrain
import com.rk.ai.icons.SparklesBig
import com.rk.tabs.base.Tab

class AiTab : Tab() {
    override val name: String = "AI"
    override val icon: ImageVector = SparklesBig
    override val title: String = "AI"

    private var controller: AiChatController = AiChatController()

    @Composable
    override fun Content() {
        AiChatScreen(controller = controller)
    }

    @Composable
    override fun RowScope.Actions() {}

    override fun onTabRemoved() {
        controller.dispose()
    }
}
