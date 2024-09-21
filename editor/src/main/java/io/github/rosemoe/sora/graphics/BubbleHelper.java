/*
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
 */
package io.github.rosemoe.sora.graphics;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Helper class for building a bubble rect
 *
 * @author Rosemoe
 */
public class BubbleHelper {

    private final static Matrix tempMatrix = new Matrix();

    /**
     * Build a bubble into the given Path object. Old content in given Path is cleared.
     * @param path target Path object
     * @param bounds The bounds for the bubble
     */
    public static void buildBubblePath(@NonNull Path path, @NonNull RectF bounds) {
        path.reset();

        float width = bounds.width();
        float height = bounds.height();
        float r = height / 2;
        float sqrt2 = (float) Math.sqrt(2);
        // Ensure we are convex.
        width = Math.max(r + sqrt2 * r, width);
        pathArcTo(path, r, r, r, 90, 180);
        float o1X = width - sqrt2 * r;
        pathArcTo(path, o1X, r, r, -90, 45f);
        float r2 = r / 5;
        float o2X = width - sqrt2 * r2;
        pathArcTo(path, o2X, r, r2, -45, 90);
        pathArcTo(path, o1X, r, r, 45f, 45f);
        path.close();

        tempMatrix.reset();
        tempMatrix.postTranslate(bounds.left, bounds.top);
        path.transform(tempMatrix);
    }

    private static void pathArcTo(@NonNull Path path, float centerX, float centerY, float radius,
                                  float startAngle, float sweepAngle) {
        path.arcTo(centerX - radius, centerY - radius, centerX + radius, centerY + radius,
                startAngle, sweepAngle, false);
    }

}
