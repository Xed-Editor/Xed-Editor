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

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import com.rk.components.compose.edges.StretchEdgeEffect

/**
 * Creates a custom overscroll effect based off the Android 12 "stretch" animation.
 *
 * @param content The content to animate.
 *
 * TODO: Allow horizontal stretch
 */
@Composable
fun NestedScrollStretch(modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val invalidateTick = remember { mutableIntStateOf(0) }
    val invalidate = Runnable { invalidateTick.intValue++ }

    val context = LocalContext.current
    val connection = remember { NestedScrollStretchConnection(context, invalidate) }
    connection.enabled = enabled

    val tmpOut = remember { FloatArray(5) }

    Box(
        modifier =
            modifier
                .nestedScroll(connection)
                .onSizeChanged {
                    connection.width = it.width
                    connection.height = it.height
                    connection.topEdgeEffect.setSize(it.width, it.height)
                    connection.bottomEdgeEffect.setSize(it.width, it.height)
                    connection.leftEdgeEffect.setSize(it.height, it.width)
                    connection.rightEdgeEffect.setSize(it.height, it.width)
                }
                .drawWithContent {
                    // Redraw when this value changes
                    invalidateTick.intValue

                    connection.topEdgeEffect.draw(tmpOut, StretchEdgeEffect.POSITION_TOP, this) {
                        connection.bottomEdgeEffect.draw(tmpOut, StretchEdgeEffect.POSITION_BOTTOM, this) {
                            connection.leftEdgeEffect.draw(tmpOut, StretchEdgeEffect.POSITION_LEFT, this) {
                                connection.rightEdgeEffect.draw(tmpOut, StretchEdgeEffect.POSITION_RIGHT, this) {
                                    drawContent()
                                }
                            }
                        }
                    }
                }
    ) {
        content()
    }
}

private inline fun StretchEdgeEffect.draw(
    tmpOut: FloatArray,
    @StretchEdgeEffect.EdgeEffectPosition position: Int,
    scope: DrawScope,
    crossinline block: () -> Unit,
) {
    if (isFinished) {
        block()
        return
    }

    tmpOut[0] = 0f
    getScale(tmpOut, position)
    if (tmpOut[0] == 1f) {
        scope.scale(tmpOut[1], tmpOut[2], pivot = Offset(tmpOut[3], tmpOut[4])) { block() }
    } else {
        block()
    }
}

private class NestedScrollStretchConnection(context: Context, invalidate: Runnable) : NestedScrollConnection {

    var width = 0
    var height = 0
    var enabled = true
        set(value) {
            if (field && !value) {
                // Release any stretch that's currently in progress so it doesn't freeze
                topEdgeEffect.onRelease()
                bottomEdgeEffect.onRelease()
                leftEdgeEffect.onRelease()
                rightEdgeEffect.onRelease()
            }
            field = value
        }

    val topEdgeEffect = StretchEdgeEffect(context, invalidate, invalidate)
    val bottomEdgeEffect = StretchEdgeEffect(context, invalidate, invalidate)
    val leftEdgeEffect = StretchEdgeEffect(context, invalidate, invalidate)
    val rightEdgeEffect = StretchEdgeEffect(context, invalidate, invalidate)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabled) return Offset.Zero
        
        var consumedX = 0f
        var consumedY = 0f
        
        if (source == NestedScrollSource.UserInput) {
            val availableX = available.x
            if (availableX != 0f && width != 0) {
                if (availableX < 0f) {
                    consumedX = leftEdgeEffect.onPullDistance(availableX / width, 0f)
                    if (leftEdgeEffect.distance == 0f) leftEdgeEffect.onRelease()
                } else {
                    consumedX = rightEdgeEffect.onPullDistance(-availableX / width, 0f)
                    if (rightEdgeEffect.distance == 0f) rightEdgeEffect.onRelease()
                }
            }
            
            val availableY = available.y
            if (availableY != 0f && height != 0) {
                if (availableY < 0f) {
                    consumedY = topEdgeEffect.onPullDistance(availableY / height, 0f)
                    if (topEdgeEffect.distance == 0f) topEdgeEffect.onRelease()
                } else {
                    consumedY = bottomEdgeEffect.onPullDistance(-availableY / height, 0f)
                    if (bottomEdgeEffect.distance == 0f) bottomEdgeEffect.onRelease()
                }
            }
        }
        
        return Offset(consumedX * width, consumedY * height)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!enabled) return Offset.Zero
        
        if (source == NestedScrollSource.UserInput) {
            val availableX = available.x
            if (availableX != 0f && width != 0) {
                if (availableX > 0f) {
                    leftEdgeEffect.onPull(availableX / width)
                } else {
                    rightEdgeEffect.onPull(-availableX / width)
                }
            }
            
            val availableY = available.y
            if (availableY != 0f && height != 0) {
                if (availableY > 0f) {
                    topEdgeEffect.onPull(availableY / height)
                } else {
                    bottomEdgeEffect.onPull(-availableY / height)
                }
            }
        } else {
            topEdgeEffect.onRelease()
            bottomEdgeEffect.onRelease()
            leftEdgeEffect.onRelease()
            rightEdgeEffect.onRelease()
        }
        return available
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        topEdgeEffect.onRelease()
        bottomEdgeEffect.onRelease()
        leftEdgeEffect.onRelease()
        rightEdgeEffect.onRelease()
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!enabled) return Velocity.Zero
        
        if (width != 0) {
            val availableX = available.x
            if (availableX > 0f) {
                leftEdgeEffect.onAbsorb(availableX.toInt())
            } else if (availableX < 0f) {
                rightEdgeEffect.onAbsorb(-availableX.toInt())
            }
        }
        
        if (height != 0) {
            val availableY = available.y
            if (availableY > 0f) {
                topEdgeEffect.onAbsorb(availableY.toInt())
            } else if (availableY < 0f) {
                bottomEdgeEffect.onAbsorb(-availableY.toInt())
            }
        }
        
        return available
    }
}
