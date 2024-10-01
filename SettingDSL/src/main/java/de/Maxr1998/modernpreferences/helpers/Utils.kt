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

package de.Maxr1998.modernpreferences.helpers

import android.widget.SeekBar

const val KEY_ROOT_SCREEN = "root"

/**
 * A resource ID as a default value for optional attributes.
 */
internal const val DISABLED_RESOURCE_ID = -1

@Deprecated("This constant was accidentally exposed and should not be used.")
const val DEFAULT_RES_ID = DISABLED_RESOURCE_ID

internal fun SeekBar.onSeek(callback: (Int, Boolean) -> Unit) {
    setOnSeekBarChangeListener(
        object : SeekBar.OnSeekBarChangeListener {
            @Suppress("EmptyFunctionBlock")
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) callback(progress, false)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                callback(seekBar.progress, true)
            }
        },
    )
}