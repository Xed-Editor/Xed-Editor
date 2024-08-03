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

@file:Suppress("unused", "TooManyFunctions")

package de.Maxr1998.modernpreferences.helpers

import android.content.Context
import android.view.View
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.preferences.AccentButtonPreference
import de.Maxr1998.modernpreferences.preferences.CategoryHeader
import de.Maxr1998.modernpreferences.preferences.CheckBoxPreference
import de.Maxr1998.modernpreferences.preferences.CollapsePreference
import de.Maxr1998.modernpreferences.preferences.EditTextPreference
import de.Maxr1998.modernpreferences.preferences.ExpandableTextPreference
import de.Maxr1998.modernpreferences.preferences.ImagePreference
import de.Maxr1998.modernpreferences.preferences.SeekBarPreference
import de.Maxr1998.modernpreferences.preferences.SwitchPreference
import de.Maxr1998.modernpreferences.preferences.TwoStatePreference
import de.Maxr1998.modernpreferences.preferences.choice.AbstractSingleChoiceDialogPreference
import de.Maxr1998.modernpreferences.preferences.choice.MultiChoiceDialogPreference
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import de.Maxr1998.modernpreferences.preferences.choice.SingleChoiceDialogPreference
import de.Maxr1998.modernpreferences.preferences.choice.SingleIntChoiceDialogPreference

// PreferenceScreen DSL functions
inline fun screen(context: Context?, block: PreferenceScreen.Builder.() -> Unit): PreferenceScreen {
    return PreferenceScreen.Builder(context).apply(block).build()
}

inline fun PreferenceScreen.Builder.subScreen(
    key: String = "",
    block: PreferenceScreen.Builder.() -> Unit,
): PreferenceScreen {
    return PreferenceScreen.Builder(this, key).apply(block).build().also(::addPreferenceItem)
}

// Preference DSL functions
inline fun PreferenceScreen.Appendable.categoryHeader(key: String, block: Preference.() -> Unit) {
    addPreferenceItem(CategoryHeader(key).apply(block))
}

inline fun PreferenceScreen.Appendable.pref(key: String, block: Preference.() -> Unit): Preference {
    return Preference(key).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.accentButtonPref(key: String, block: Preference.() -> Unit): Preference {
    return AccentButtonPreference(key).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.switch(key: String, block: SwitchPreference.() -> Unit): SwitchPreference {
    return SwitchPreference(key).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.checkBox(key: String, block: CheckBoxPreference.() -> Unit): CheckBoxPreference {
    return CheckBoxPreference(key).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.image(key: String, block: ImagePreference.() -> Unit): ImagePreference {
    return ImagePreference(key).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.seekBar(key: String, block: SeekBarPreference.() -> Unit): SeekBarPreference {
    return SeekBarPreference(key).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.expandText(
    key: String,
    block: ExpandableTextPreference.() -> Unit,
): ExpandableTextPreference {
    return ExpandableTextPreference(key).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.singleChoice(
    key: String,
    items: List<SelectionItem<String>>,
    block: SingleChoiceDialogPreference.() -> Unit,
): SingleChoiceDialogPreference {
    return SingleChoiceDialogPreference(key, items).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.singleChoice(
    key: String,
    items: List<SelectionItem<Int>>,
    block: SingleIntChoiceDialogPreference.() -> Unit,
): SingleIntChoiceDialogPreference {
    return SingleIntChoiceDialogPreference(key, items).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.multiChoice(
    key: String,
    items: List<SelectionItem<String>>,
    block: MultiChoiceDialogPreference.() -> Unit,
): MultiChoiceDialogPreference {
    return MultiChoiceDialogPreference(key, items).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Appendable.editText(key: String, block: EditTextPreference.() -> Unit): EditTextPreference {
    return EditTextPreference(key).apply(block).also(::addPreferenceItem)
}

inline fun <reified T : Preference> PreferenceScreen.Appendable.custom(key: String, block: T.() -> Unit): T {
    return T::class.java.getConstructor(String::class.java).newInstance(key).apply(block).also(::addPreferenceItem)
}

inline fun PreferenceScreen.Builder.collapse(
    key: String = "advanced",
    block: CollapsePreference.() -> Unit,
): CollapsePreference {
    return CollapsePreference(this, key).also(::addPreferenceItem).apply {
        block()
        clearContext()
    }
}

inline fun CollapsePreference.subScreen(key: String = "", block: PreferenceScreen.Builder.() -> Unit) {
    addPreferenceItem(PreferenceScreen.Builder(this, key).apply(block).build())
}

// Listener helpers

/**
 * [Preference.OnClickListener] shorthand without parameters.
 * Callback return value determines whether the Preference changed/requires a rebind.
 */
inline fun Preference.onClick(crossinline callback: () -> Boolean) {
    clickListener = Preference.OnClickListener { _, _ -> callback() }
}

/**
 * [Preference.OnClickListener] shorthand without parameters that returns false by default,
 * meaning the Preference didn't get changed and doesn't require a rebind/redraw.
 */
inline fun Preference.defaultOnClick(crossinline callback: () -> Unit) {
    clickListener = Preference.OnClickListener { _, _ ->
        callback()
        false
    }
}

/**
 * [Preference.OnClickListener] shorthand that only passes the view of the clicked item and returns false by default,
 * meaning the Preference didn't get changed and doesn't require a rebind/redraw.
 */
inline fun Preference.onClickView(crossinline callback: (View) -> Unit) {
    clickListener = Preference.OnClickListener { _, holder ->
        callback(holder.itemView)
        false
    }
}

/**
 * [TwoStatePreference.OnCheckedChangeListener] shorthand.
 * Supplies the changed state, return value determines whether that state should be persisted
 * to [SharedPreferences][android.content.SharedPreferences].
 */
inline fun TwoStatePreference.onCheckedChange(crossinline callback: (Boolean) -> Boolean) {
    checkedChangeListener = TwoStatePreference.OnCheckedChangeListener { _, _, checked ->
        callback(checked)
    }
}

/**
 * [TwoStatePreference.OnCheckedChangeListener] shorthand.
 * Always persists the change to [SharedPreferences][android.content.SharedPreferences].
 */
inline fun TwoStatePreference.defaultOnCheckedChange(crossinline callback: (Boolean) -> Unit) {
    checkedChangeListener = TwoStatePreference.OnCheckedChangeListener { _, _, checked ->
        callback(checked)
        true
    }
}

/**
 * [AbstractSingleChoiceDialogPreference.OnSelectionChangeListener] shorthand.
 * Supplies the changed selection, return value determines whether that state should be persisted
 * to [SharedPreferences][android.content.SharedPreferences].
 */
inline fun SingleChoiceDialogPreference.onSelectionChange(crossinline callback: (String) -> Boolean) {
    selectionChangeListener = AbstractSingleChoiceDialogPreference.OnSelectionChangeListener { _, selection ->
        callback(selection)
    }
}

/**
 * [AbstractSingleChoiceDialogPreference.OnSelectionChangeListener] shorthand.
 * Always persists the change to [SharedPreferences][android.content.SharedPreferences].
 */
inline fun SingleChoiceDialogPreference.defaultOnSelectionChange(crossinline callback: (String) -> Unit) {
    selectionChangeListener = AbstractSingleChoiceDialogPreference.OnSelectionChangeListener { _, selection ->
        callback(selection)
        true
    }
}

/**
 * [MultiChoiceDialogPreference.OnSelectionChangeListener] shorthand.
 * Supplies the changed selections, return value determines whether that state should be persisted
 * to [SharedPreferences][android.content.SharedPreferences].
 */
inline fun MultiChoiceDialogPreference.onSelectionChange(crossinline callback: (Set<String>) -> Boolean) {
    selectionChangeListener = MultiChoiceDialogPreference.OnSelectionChangeListener { _, selection ->
        callback(selection)
    }
}

/**
 * [MultiChoiceDialogPreference.OnSelectionChangeListener] shorthand.
 * Always persists the change to [SharedPreferences][android.content.SharedPreferences].
 */
inline fun MultiChoiceDialogPreference.defaultOnSelectionChange(crossinline callback: (Set<String>) -> Unit) {
    selectionChangeListener = MultiChoiceDialogPreference.OnSelectionChangeListener { _, selection ->
        callback(selection)
        true
    }
}