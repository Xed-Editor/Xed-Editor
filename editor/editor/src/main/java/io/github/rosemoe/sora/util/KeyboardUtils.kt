/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/

package io.github.rosemoe.sora.util

import android.content.Context
import android.content.res.Configuration

import android.inputmethodservice.InputMethodService

/**
 * Utility functions for keyboard.
 *
 * @author Akash Yadav
 */
object KeyboardUtils {

    /**
     * Check if hardware keyboard is connected.
     * Based on default implementation of [InputMethodService.onEvaluateInputViewShown].
     *
     * https://developer.android.com/guide/topics/resources/providing-resources#ImeQualifier
     *
     * @param context The Context for operations.
     * @return Returns `true` if device has hardware keys for text input or an external hardware
     * keyboard is connected, otherwise `false`.
     */
    fun isHardKeyboardConnected(context: Context?): Boolean {
        if (context == null) return false
        val config = context.resources.configuration
        return (config.keyboard != Configuration.KEYBOARD_NOKEYS
                || config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO)
    }
}