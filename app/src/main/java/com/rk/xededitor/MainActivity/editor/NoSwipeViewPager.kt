package com.rk.xededitor.MainActivity.editor

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

class NoSwipeViewPager : ViewPager {
    private var startX = 0f
    private var startY = 0f

    constructor(context: Context?) : super(context!!)

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only allow vertical scrolling
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs((event.x - startX).toDouble()).toFloat()
                val dy = abs((event.y - startY).toDouble()).toFloat()
                if (dy > dx) {
                    // Vertical scroll
                    return super.onTouchEvent(event)
                }
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Intercept touch event only for vertical scrolling
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs((event.x - startX).toDouble()).toFloat()
                val dy = abs((event.y - startY).toDouble()).toFloat()
                if (dy > dx) {
                    // Vertical scroll
                    return super.onInterceptTouchEvent(event)
                }
            }
        }
        return false
    }
}

