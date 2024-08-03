/*
 * Copyright (C) 2018 Max Rumpf alias Maxr1998
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.Maxr1998.modernpreferences.preferences

import android.view.LayoutInflater
import android.widget.Space
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.R
import de.Maxr1998.modernpreferences.helpers.onSeek
import de.Maxr1998.modernpreferences.views.SeekBar

class SeekBarPreference(key: String) : Preference(key) {

    var min = 0
    var max = 0
    var default: Int? = null
    var step = 1
        set(value) {
            require(value > 0) { "Stepping value must be >= 1" }
            field = value
        }

    var showTickMarks = false

    /**
     * The internal backing field of [value]
     */
    private var valueInternal = 0
    var value: Int
        get() = valueInternal
        set(v) {
            if (v != valueInternal && seekListener?.onSeek(this, null, v) != false) {
                valueInternal = v
                commitInt(value)
                requestRebind()
            }
        }

    var seekListener: OnSeekListener? = null
    var formatter: (Int) -> String = Int::toString

    override fun getWidgetLayoutResource() = R.layout.map_preference_widget_seekbar_stub

    override fun onAttach() {
        check(min <= max) { "Minimum value can't be greater than maximum!" }
        default?.let { default ->
            check(default in min..max) { "Default value must be in between min and max!" }
        }
        valueInternal = getInt(default ?: min)
    }

    override fun bindViews(holder: PreferencesAdapter.ViewHolder) {
        super.bindViews(holder)
        holder.root.apply {
            background = null
            clipChildren = false
        }
        holder.iconFrame.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            @Suppress("MagicNumber")
            bottomMargin = (40 * holder.itemView.resources.displayMetrics.density).toInt()
        }
        holder.title.updateLayoutParams<ConstraintLayout.LayoutParams> {
            goneBottomMargin = 0
        }
        holder.summary?.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomMargin = 0
        }
        val widget = holder.widget as Space
        val sb = widget.tag as? SeekBar ?: run {
            val inflater = LayoutInflater.from(widget.context)
            inflater.inflate(R.layout.map_preference_widget_seekbar, holder.root).findViewById(android.R.id.progress)
        }
        val tv = sb.tag as? TextView ?: holder.itemView.findViewById(R.id.progress_text)
        widget.tag = sb.apply {
            isEnabled = enabled
            max = calcRaw(this@SeekBarPreference.max)
            progress = calcRaw(valueInternal)
            hasTickMarks = showTickMarks
            this@SeekBarPreference.default?.let { default = calcRaw(it) }

            onSeek { v, done ->
                if (done) {
                    // Commit the last selected value
                    commitInt(valueInternal)
                } else {
                    val next = calcValue(v)
                    // Check if listener allows the value change
                    if (seekListener?.onSeek(this@SeekBarPreference, holder, next) != false) {
                        // Update internal value
                        valueInternal = next
                    } else {
                        // Restore previous value
                        progress = calcRaw(valueInternal)
                    }
                    // Update preview text
                    tv.text = formatter(valueInternal)
                }
            }
        }
        sb.tag = tv.apply {
            isEnabled = enabled
            text = formatter(valueInternal)
        }
    }

    private fun calcRaw(value: Int) = (value - min) / step
    private fun calcValue(raw: Int) = min + raw * step

    fun interface OnSeekListener {
        /**
         * Notified when the [value][SeekBarPreference.value] of the connected [SeekBarPreference] changes.
         * This is called *before* the change gets persisted, which can be prevented by returning false.
         *
         * @param holder the [ViewHolder][PreferencesAdapter.ViewHolder] with the views of the Preference instance,
         * or null if the change didn't occur as part of a click event
         * @param value the new state
         *
         * @return true to commit the new slider value to [SharedPreferences][android.content.SharedPreferences]
         */
        fun onSeek(preference: SeekBarPreference, holder: PreferencesAdapter.ViewHolder?, value: Int): Boolean
    }
}