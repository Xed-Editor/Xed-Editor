package com.rk.components.compose.preferences.base

/*
 * Copyright 2021, Lawnchair.
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Animates the appearance and disappearance of [content] via an expanding and shrinking animation,
 * respectively.
 *
 * @param visible Defines whether the content should be visible
 * @param content Content to appear or disappear based on the value of [visible]
 */
@Composable
fun ExpandAndShrink(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        content = content,
        modifier = modifier,
    )
}
