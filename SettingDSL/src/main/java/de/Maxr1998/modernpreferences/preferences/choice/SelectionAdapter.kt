package de.Maxr1998.modernpreferences.preferences.choice

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.content.res.use
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import de.Maxr1998.modernpreferences.R
import de.Maxr1998.modernpreferences.helpers.DISABLED_RESOURCE_ID

internal class SelectionAdapter<T : Any>(
    private val preference: AbstractChoiceDialogPreference<T>,
    private val items: List<SelectionItem<T>>,
    private val allowMultiSelect: Boolean,
) : RecyclerView.Adapter<SelectionAdapter.SelectionViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectionViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val layout = when {
            allowMultiSelect -> R.layout.map_dialog_multi_choice_item
            else -> R.layout.map_dialog_single_choice_item
        }
        val view = layoutInflater.inflate(layout, parent, false)
        return SelectionViewHolder(view)
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    override fun onBindViewHolder(holder: SelectionViewHolder, position: Int) {
        val item = items[position]
        holder.apply {
            selector.isChecked = preference.isSelected(item)
            title.apply {
                when {
                    item.titleRes != DISABLED_RESOURCE_ID -> setText(item.titleRes)
                    else -> text = item.title
                }
            }
            summary.apply {
                when {
                    item.summaryRes != DISABLED_RESOURCE_ID -> setText(item.summaryRes)
                    else -> text = item.summary
                }
                isVisible = item.summaryRes != DISABLED_RESOURCE_ID || item.summary != null
            }
            if (item.badgeInfo != null) {
                badge.apply {
                    when {
                        item.badgeInfo.textRes != DISABLED_RESOURCE_ID -> setText(item.badgeInfo.textRes)
                        else -> text = item.badgeInfo.text
                    }
                    isVisible = item.badgeInfo.isVisible
                }
                setBadgeColor(item.badgeInfo.badgeColor)
            } else {
                badge.isVisible = false
            }
            itemView.setOnClickListener {
                if (preference.shouldSelect(item)) {
                    preference.select(item)
                    when {
                        allowMultiSelect -> notifyItemChanged(position)
                        else -> notifySelectionChanged()
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun notifySelectionChanged() {
        notifyItemRangeChanged(0, itemCount)
    }

    class SelectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val selector: CompoundButton = itemView.findViewById(R.id.map_selector)
        val title: TextView = itemView.findViewById(android.R.id.title)
        val summary: TextView = itemView.findViewById(android.R.id.summary)
        val badge: TextView = itemView.findViewById(R.id.badge)

        private val accentTextColor: ColorStateList

        init {
            // Apply accent text color via theme attribute from library or fallback to AppCompat
            val attrs = intArrayOf(R.attr.mapAccentTextColor, androidx.appcompat.R.attr.colorAccent)
            accentTextColor = itemView.context.theme.obtainStyledAttributes(attrs).use { array ->
                // Return first resolved attribute or null
                when {
                    array.indexCount > 0 -> array.getColorStateList(array.getIndex(0))
                    else -> null
                }
            } ?: ColorStateList.valueOf(Color.BLACK) // fallback to black if no colorAccent is defined (unlikely)

            // Set initial badge color
            setBadgeColor(null)
        }

        internal fun setBadgeColor(color: ColorStateList?) {
            badge.apply {
                setTextColor(color ?: accentTextColor)
                backgroundTintList = color ?: accentTextColor
                backgroundTintMode = PorterDuff.Mode.SRC_ATOP
            }
        }
    }
}