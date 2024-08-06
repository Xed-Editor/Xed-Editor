package de.Maxr1998.modernpreferences.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import de.Maxr1998.modernpreferences.R

class SeekBar(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
) : AppCompatSeekBar(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, androidx.appcompat.R.attr.seekBarStyle)
    constructor(context: Context) : this(context, null)

    private var tickMarkDrawable: Drawable? = null
        set(value) {
            field?.callback = null
            field = value

            if (value != null) {
                value.callback = this
                DrawableCompat.setLayoutDirection(value, ViewCompat.getLayoutDirection(this))
                if (value.isStateful) value.state = drawableState
            }

            invalidate()
        }

    private var defaultMarkerDrawable: Drawable? = null
        set(value) {
            field?.callback = null
            field = value

            if (value != null) {
                value.callback = this
                DrawableCompat.setLayoutDirection(value, ViewCompat.getLayoutDirection(this))
                if (value.isStateful) value.state = drawableState
            }

            invalidate()
        }

    var hasTickMarks
        get() = tickMarkDrawable != null
        set(value) {
            tickMarkDrawable = when {
                value -> when (tickMarkDrawable) {
                    null -> ContextCompat.getDrawable(context, R.drawable.map_seekbar_tick_mark)
                    else -> tickMarkDrawable
                }
                else -> null
            }
        }

    var default: Int? = null
        set(value) {
            require(value == null || value in 0..max) {
                "Default must be in range 0 to max (is $value)"
            }
            field = value
            if (value != null) {
                if (defaultMarkerDrawable == null) {
                    defaultMarkerDrawable = ContextCompat.getDrawable(context, R.drawable.map_seekbar_default_marker)
                }
            } else defaultMarkerDrawable = null
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawTickMarks(canvas)
        drawDefaultMarker(canvas)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        fun stateChanged(d: Drawable?) {
            if (d != null && d.isStateful && d.setState(drawableState)) {
                invalidateDrawable(d)
            }
        }
        stateChanged(tickMarkDrawable)
        stateChanged(defaultMarkerDrawable)
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        tickMarkDrawable?.jumpToCurrentState()
        defaultMarkerDrawable?.jumpToCurrentState()
    }

    private fun drawTickMarks(canvas: Canvas) {
        tickMarkDrawable?.let { tickMark ->
            val count = max
            if (count > 0) {
                val w = tickMark.intrinsicWidth
                val h = tickMark.intrinsicHeight
                val halfW = if (w >= 0) w / 2 else 1
                val halfH = if (h >= 0) h / 2 else 1
                tickMark.setBounds(-halfW, -halfH, halfW, halfH)
                val spacing: Float = (width - paddingLeft - paddingRight) / count.toFloat()
                val saveCount = canvas.save()
                canvas.translate(paddingLeft.toFloat(), height / 2.toFloat())
                repeat(count + 1) {
                    tickMark.draw(canvas)
                    canvas.translate(spacing, 0f)
                }
                canvas.restoreToCount(saveCount)
            }
        }
    }

    private fun drawDefaultMarker(canvas: Canvas) {
        val default = default ?: return
        if (default == progress) return
        defaultMarkerDrawable?.let { defaultMarker ->
            val w: Int = defaultMarker.intrinsicWidth
            val h: Int = defaultMarker.intrinsicHeight
            val halfW = if (w >= 0) w / 2 else 1
            val halfH = if (h >= 0) h / 2 else 1
            defaultMarker.setBounds(-halfW, -halfH, halfW, halfH)
            val saveCount = canvas.save()
            val spacing: Float = default * (width - paddingLeft - paddingRight) / max.toFloat()
            canvas.translate(paddingLeft.toFloat() + spacing, height / 2.toFloat())
            defaultMarker.draw(canvas)
            canvas.restoreToCount(saveCount)
        }
    }
}