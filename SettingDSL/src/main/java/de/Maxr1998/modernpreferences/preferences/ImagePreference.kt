/*
 * Copyright (C) 2019 Max Rumpf alias Maxr1998
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

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.R
import de.Maxr1998.modernpreferences.helpers.DISABLED_RESOURCE_ID

/**
 * Shows a drawable inside an ImageView
 *
 * The drawable can be set via [imageRes], [imageDrawable], or lazily via [lazyImage];
 * with priorities being evaluated in the same order.
 *
 * By default, a scrim is shown over the image to improve [title] legibility, which can be disabled
 * by setting [showScrim] to false.
 *
 * The image can be tinted by applying a [ColorFilter], either via [tint],
 * or through the helper method [setTintColor].
 */
@Suppress("MemberVisibilityCanBePrivate")
class ImagePreference(key: String) : Preference(key) {
    override fun getWidgetLayoutResource() = RESOURCE_CONST

    @DrawableRes
    var imageRes: Int = DISABLED_RESOURCE_ID
    var imageDrawable: Drawable? = null
    var lazyImage: (() -> Drawable)? = null

    var maxImageHeight = Integer.MAX_VALUE

    var showScrim = true
    var tint: ColorFilter? = null

    fun setTintColor(@ColorInt color: Int, mode: PorterDuff.Mode = PorterDuff.Mode.SRC_ATOP) {
        tint = PorterDuffColorFilter(color, mode)
    }

    override fun bindViews(holder: PreferencesAdapter.ViewHolder) {
        super.bindViews(holder)
        val image = holder.root.findViewById<ImageView>(R.id.map_image)
        when {
            imageRes != DISABLED_RESOURCE_ID -> image.setImageResource(imageRes)
            imageDrawable != null -> image.setImageDrawable(imageDrawable)
            lazyImage != null -> image.setImageDrawable(lazyImage?.invoke())
            else -> image.setImageDrawable(null)
        }
        image.maxHeight = maxImageHeight
        image.colorFilter = tint
        holder.root.findViewById<ImageView>(R.id.map_image_scrim).isVisible = showScrim && title.isNotBlank()
    }

    internal companion object {
        internal const val RESOURCE_CONST = -4
    }
}