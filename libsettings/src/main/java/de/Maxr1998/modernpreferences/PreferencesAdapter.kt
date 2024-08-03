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

package de.Maxr1998.modernpreferences

import android.animation.StateListAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.core.content.res.use
import androidx.core.view.get
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.Maxr1998.modernpreferences.preferences.AccentButtonPreference
import de.Maxr1998.modernpreferences.preferences.CategoryHeader
import de.Maxr1998.modernpreferences.preferences.CollapsePreference
import de.Maxr1998.modernpreferences.preferences.ImagePreference
import de.Maxr1998.modernpreferences.preferences.SeekBarPreference
import kotlinx.parcelize.Parcelize
import java.util.Stack
import kotlin.math.max

@Suppress("MemberVisibilityCanBePrivate", "NotifyDataSetChanged")
class PreferencesAdapter @VisibleForTesting constructor(
    root: PreferenceScreen? = null,
    hasStableIds: Boolean,
) : RecyclerView.Adapter<PreferencesAdapter.ViewHolder>(),
    LifecycleEventObserver {

    private companion object {
        private val emptyScreen: PreferenceScreen by lazy {
            PreferenceScreen.Builder(null).build()
        }
    }

    constructor(root: PreferenceScreen? = null) : this(root, true)

    private val screenStack: Stack<PreferenceScreen> = Stack<PreferenceScreen>().apply {
        push(emptyScreen)
    }

    val currentScreen: PreferenceScreen
        get() = screenStack.peek()

    /**
     * Listener which will be notified before screen change events
     */
    var beforeScreenChangeListener: BeforeScreenChangeListener? = null

    /**
     * Listener which will be notified of screen change events
     *
     * Will dispatch the initial state when attached.
     */
    var onScreenChangeListener: OnScreenChangeListener? = null
        set(value) {
            field = value
            field?.onScreenChanged(currentScreen, isInSubScreen())
        }

    /**
     * A [StateListAnimator] that will be applied to all Preference item views
     *
     * *Attention*: This animator will be cloned using [Object.clone] before getting applied.
     */
    var stateListAnimator: StateListAnimator? = null

    var secondScreenAdapter: PreferencesAdapter? = null

    init {
        // Necessary for testing, because setHasStableIds calls into an (in the stubbed android.jar)
        // uninitialized observer list which causes a NPE
        if (hasStableIds) {
            setHasStableIds(true)
        }
        root?.let(::setRootScreen)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if (recyclerView.layoutManager !is LinearLayoutManager) {
            throw UnsupportedOperationException("ModernAndroidPreferences requires a LinearLayoutManager")
        }
        recyclerView.findViewTreeLifecycleOwner()?.lifecycle?.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        currentScreen.notifyLifecycleObservers(source, event)
    }

    @MainThread
    fun setRootScreen(root: PreferenceScreen) {
        currentScreen.adapter = null
        while (screenStack.peek() != emptyScreen) {
            screenStack.pop()
        }
        screenStack.push(root)
        notifyDataSetChanged()
        currentScreen.adapter = this
        onScreenChangeListener?.onScreenChanged(root, false)
    }

    @VisibleForTesting
    @MainThread
    internal fun openScreen(screen: PreferenceScreen) {
        secondScreenAdapter?.setRootScreen(screen) ?: run { // ELSE
            if (beforeScreenChangeListener?.beforeScreenChange(screen) == false) {
                return
            }

            currentScreen.adapter = null
            screenStack.push(screen)
            notifyDataSetChanged()
            currentScreen.adapter = this
            onScreenChangeListener?.onScreenChanged(screen, true)
        }
    }

    fun isInSubScreen() = screenStack.size > 2

    /**
     * If possible, return to the previous screen.
     *
     * @return true if it returned to an earlier screen, false if we're already at the root
     */
    @MainThread
    fun goBack(): Boolean = when {
        // Check if the second screen can still go back
        secondScreenAdapter?.goBack() == true -> true
        // Can't go back when not in a subscreen
        !isInSubScreen() -> false
        // Callback may disallow screen change
        beforeScreenChangeListener?.beforeScreenChange(screenStack[screenStack.size - 2]) == false -> true
        // Change screens!
        else -> {
            currentScreen.adapter = null
            val oldScreen = screenStack.pop() // ...remove current screen from stack
            notifyDataSetChanged()
            currentScreen.adapter = this
            onScreenChangeListener?.onScreenChanged(currentScreen, isInSubScreen())
            for (i in 0 until oldScreen.size()) {
                val p = oldScreen[i]
                if (p.javaClass == CollapsePreference::class.java) {
                    (p as CollapsePreference).reset()
                }
            }
            true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val layout = when (viewType) {
            CategoryHeader.RESOURCE_CONST -> R.layout.map_preference_category
            AccentButtonPreference.RESOURCE_CONST -> R.layout.map_accent_button_preference
            ImagePreference.RESOURCE_CONST -> R.layout.map_image_preference
            else -> R.layout.map_preference
        }

        // Inflate main preference view
        val view = layoutInflater.inflate(layout, parent, false)
        view.stateListAnimator = try {
            stateListAnimator?.clone()
        } catch (e: NoSuchMethodError) {
            // Some awful Android 5 devices apparently don't implement the clone method,
            // although it's part of the Android SDK since API 21. Thus, we catch it and return null instead.
            Log.e("PreferencesAdapter", "Missing `clone()` method, stateListAnimator won't work for preferences", e)
            null
        }

        // Inflate preference widget
        if (viewType > 0) {
            layoutInflater.inflate(viewType, view.findViewById(R.id.map_widget_frame), true)
        }

        return ViewHolder(viewType, view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pref = currentScreen[position]
        pref.bindViews(holder)

        // Category header and seek bar shouldn't be clickable
        if (pref is CategoryHeader || pref is SeekBarPreference) return

        holder.itemView.setOnClickListener {
            if (pref is PreferenceScreen) {
                openScreen(pref) // Navigate to sub screen
            } else {
                pref.performClick(holder)
            }
        }
    }

    override fun getItemCount() = currentScreen.size()

    override fun getItemId(position: Int) = currentScreen[position].hashCode().toLong()

    @LayoutRes
    override fun getItemViewType(position: Int) = currentScreen[position].getWidgetLayoutResource()

    /**
     * Restores the last scroll position if needed and (re-)attaches this adapter's scroll listener.
     *
     * Should be called from [OnScreenChangeListener.onScreenChanged].
     */
    fun restoreAndObserveScrollPosition(preferenceView: RecyclerView) {
        with(currentScreen) {
            if (scrollPosition != 0 || scrollOffset != 0) {
                val layoutManager = preferenceView.layoutManager as LinearLayoutManager
                layoutManager.scrollToPositionWithOffset(scrollPosition, scrollOffset)
            }
        }
        preferenceView.removeOnScrollListener(scrollListener) // We don't want to be added twice
        preferenceView.addOnScrollListener(scrollListener)
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(r: RecyclerView, state: Int) {
            if (state == RecyclerView.SCROLL_STATE_IDLE) {
                currentScreen.apply {
                    val layoutManager = r.layoutManager as LinearLayoutManager
                    scrollPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
                    scrollOffset = r.findViewHolderForAdapterPosition(scrollPosition)?.run { itemView.top } ?: 0
                }
            }
        }
    }

    /**
     * Common ViewHolder in [PreferencesAdapter] for every [Preference] object/every preference extending it
     */
    class ViewHolder internal constructor(
        type: Int,
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val root: ViewGroup get() = itemView as ViewGroup
        val iconFrame: View = itemView.findViewById(R.id.map_icon_frame)
        val icon: ImageView? = itemView.findViewById(android.R.id.icon)
        val title: TextView = itemView.findViewById(android.R.id.title)
        val summary: TextView? = itemView.findViewById(android.R.id.summary)
        val badge: TextView? = itemView.findViewById(R.id.map_badge)
        val widgetFrame: ViewGroup? = itemView.findViewById(R.id.map_widget_frame)
        val widget: View? = widgetFrame?.getChildAt(0)

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

            when (type) {
                CategoryHeader.RESOURCE_CONST,
                AccentButtonPreference.RESOURCE_CONST,
                -> title.setTextColor(accentTextColor)
            }

            // Set initial badge color
            setBadgeColor(null)
        }

        internal fun setBadgeColor(color: ColorStateList?) {
            badge?.apply {
                setTextColor(color ?: accentTextColor)
                backgroundTintList = color ?: accentTextColor
                backgroundTintMode = PorterDuff.Mode.SRC_ATOP
            }
        }

        internal fun setEnabledState(enabled: Boolean) {
            setEnabledStateRecursive(itemView, enabled)
        }

        private fun setEnabledStateRecursive(v: View, enabled: Boolean) {
            v.isEnabled = enabled
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) {
                    setEnabledStateRecursive(v[i], enabled)
                }
            }
        }
    }

    /**
     * An interface to notify observers in [PreferencesAdapter] before screen change events,
     * even allows to prevent the screen change
     */
    fun interface BeforeScreenChangeListener {
        /**
         * Called when the user attempts to switch screens by pressing on a subscreen item or going back
         *
         * @return false to prevent the change from happening
         */
        fun beforeScreenChange(screen: PreferenceScreen): Boolean
    }

    /**
     * An interface to notify observers in [PreferencesAdapter] of screen change events,
     * when a sub-screen was opened or closed
     */
    fun interface OnScreenChangeListener {
        fun onScreenChanged(screen: PreferenceScreen, subScreen: Boolean)
    }

    fun getSavedState(): SavedState {
        val screenPath = IntArray(max(0, screenStack.size - 2))
        for (i in 2 until screenStack.size) {
            screenPath[i - 2] = screenStack[i].screenPosition
        }
        return SavedState(screenPath)
    }

    /**
     * Loads the specified state into this adapter
     *
     * @return whether the state could be loaded
     */
    @MainThread
    fun loadSavedState(state: SavedState): Boolean {
        // Can't load state if we're not in the root screen
        if (screenStack.size != 2) return false

        // Restore screens from positions given in state
        for (i in state.screenPath) {
            when (val screen = currentScreen[i]) {
                is PreferenceScreen -> screenStack.push(screen)
                else -> break
            }
        }

        currentScreen.adapter = this
        notifyDataSetChanged()
        return true
    }

    @Parcelize
    data class SavedState(val screenPath: IntArray) : Parcelable {
        override fun equals(other: Any?): Boolean = when {
            this === other -> true
            else -> javaClass == other?.javaClass && screenPath.contentEquals((other as SavedState).screenPath)
        }

        override fun hashCode(): Int {
            return screenPath.contentHashCode()
        }
    }
}