package com.rk.editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rk.xededitor.R
import io.github.rosemoe.sora.widget.component.EditorCompletionAdapter
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.roundToInt

class AutoCompletionLayoutAdapter(private val density: Density) : EditorCompletionAdapter() {
    override fun getItemHeight() = with(density) { 45.dp.toPx().roundToInt() }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup?,
        isCurrentCursorPosition: Boolean,
    ): View {
        val item = getItem(position)
        val view = LayoutInflater.from(context).inflate(R.layout.completion, parent, false)

        val label: TextView = view.findViewById(R.id.result_item_label)
        label.text = item.label
        label.setTextColor(getThemeColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY))

        val desc: TextView = view.findViewById(R.id.result_item_desc)
        desc.text = item.desc
        desc.setTextColor(getThemeColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY))
        desc.visibility = if (item.desc.isNullOrEmpty()) View.GONE else View.VISIBLE

        view.tag = position

        if (isCurrentCursorPosition) {
            view.setBackgroundColor(getThemeColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT))
        } else {
            view.setBackgroundColor(0)
        }

        val iv = view.findViewById<ImageView?>(R.id.result_item_image)
        iv?.setImageDrawable(item.icon)

        return view
    }
}
