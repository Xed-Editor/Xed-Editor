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

import android.graphics.Typeface
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.R
import de.Maxr1998.modernpreferences.helpers.DISABLED_RESOURCE_ID

class ExpandableTextPreference(key: String) : Preference(key) {
    private var expanded = false

    @StringRes
    var textRes: Int = DISABLED_RESOURCE_ID
    var text: CharSequence? = null

    var monospace = true

    override fun getWidgetLayoutResource() = R.layout.map_preference_widget_expand_arrow

    override fun bindViews(holder: PreferencesAdapter.ViewHolder) {
        super.bindViews(holder)
        val widget = holder.widget as CheckBox
        val tv: TextView = widget.tag as? TextView ?: run {
            val inflater = LayoutInflater.from(widget.context)
            inflater.inflate(R.layout.map_preference_expand_text, holder.root).findViewById(android.R.id.message)
        }
        widget.tag = tv
        tv.apply {
            when {
                textRes != DISABLED_RESOURCE_ID -> setText(textRes)
                else -> text = this@ExpandableTextPreference.text
            }
            typeface = when {
                monospace -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
            with(context.obtainStyledAttributes(intArrayOf(R.attr.expandableTextBackgroundColor))) {
                val fallback = ContextCompat.getColor(context, R.color.expandableTextBackgroundColorDefault)
                setBackgroundColor(getColor(0, fallback))
                recycle()
            }
            isEnabled = enabled
        }
        refreshArrowState(widget)
        refreshTextExpandState(tv)
    }

    override fun onClick(holder: PreferencesAdapter.ViewHolder) {
        expanded = !expanded
        refreshArrowState(holder.widget as CheckBox)
        refreshTextExpandState(holder.widget.tag as TextView)
    }

    private fun refreshArrowState(widget: CheckBox) {
        widget.isChecked = expanded
    }

    private fun refreshTextExpandState(text: TextView) {
        TransitionManager.beginDelayedTransition(text.parent as ViewGroup, ChangeBounds())
        text.isVisible = expanded
    }
}