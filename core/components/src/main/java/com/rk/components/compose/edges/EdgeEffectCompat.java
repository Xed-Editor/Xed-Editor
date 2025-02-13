/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rk.components.compose.edges;

import android.content.Context;
import android.view.View;
import android.widget.EdgeEffect;

import com.rk.components.compose.utils.Utilities;

/**
 * Extension of {@link EdgeEffect} to allow backwards compatibility
 */
public class EdgeEffectCompat extends EdgeEffect {

    public EdgeEffectCompat(Context context) {
        super(context);
    }

    @Override
    public float getDistance() {
        return Utilities.ATLEAST_S ? super.getDistance() : 0;
    }

    @Override
    public float onPullDistance(float deltaDistance, float displacement) {
        if (Utilities.ATLEAST_S) {
            return super.onPullDistance(deltaDistance, displacement);
        } else {
            onPull(deltaDistance, displacement);
            return deltaDistance;
        }
    }

    public static EdgeEffectCompat create(Context context, View view) {
        if (Utilities.ATLEAST_S) {
            return new EdgeEffectCompat(context);
        } else {
            StretchEdgeEffect effect = new StretchEdgeEffect(context);
            effect.setPostInvalidateOnAnimation(view::postInvalidateOnAnimation);
            return effect;
        }
    }
}
