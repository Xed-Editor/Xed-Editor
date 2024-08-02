package com.rk.xededitor;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

import java.util.Timer;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;

import com.rk.xededitor.R;

/**
 * Copyright (c) 2016 Arlind Hajredinaj
 * <p/>
 * Permission is hereby granted, free of charge,
 * to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

public class CircleCheckBox extends View {
    private float innerCircleRadius = 30.f;
    private float outerCircleRadius = innerCircleRadius / 2f;
    private float textSize = 35f;
    private float borderThickness = 5f;
    private float tickThickness = 2f;
    private float textLeftPadding = 2f;

    private float increment = 20;
    private float total_time = 200;
    private final Paint mPaintPageFill = new Paint(ANTI_ALIAS_FLAG);
    private final Paint mPaintPageStroke = new Paint(ANTI_ALIAS_FLAG);
    private final Paint mPaintTick = new Paint(ANTI_ALIAS_FLAG);
    private final Paint mPaintOuter = new Paint(ANTI_ALIAS_FLAG);
    private final Paint mPaintText = new Paint(ANTI_ALIAS_FLAG);
    private boolean firstRun = true;
    private boolean timer_running = false;
    private float tick_third_ = innerCircleRadius / 3;
    private boolean draw_tick_part_one = false;
    private String text = "";

    private int tick_color = Color.argb(255, 255, 255, 255);
    private int text_color = Color.argb(255, 0, 0, 0);
    private int outerCircleColor = Color.argb(100, 0, 207, 173);
    private int innerCircleColor = Color.argb(255, 0, 207, 173);
    private int circleBorderColor = Color.argb(255, 0, 207, 173);

    private OnCheckedChangeListener listener;

    private boolean showOuterCircle = true;

    float centerX = 0;
    float centerY = 0;
    private boolean isChecked = false;

    private boolean draw_tick = false;

    Timer timer = new Timer();

    public CircleCheckBox(Context context) {
        super(context);
        init(context, null);
    }

    public boolean getChecked(){
        return isChecked;
    }

    public CircleCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CircleCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.CircleCheckbox,
                    0, 0);

            try {
                setTickColorHex(a.getString(R.styleable.CircleCheckbox_checkColor));
                setTextColorHex(a.getString(R.styleable.CircleCheckbox_labelColor));
                setShowOuterCircle(a.getBoolean(R.styleable.CircleCheckbox_showOuterCircle, true));
                setInnerCircleColorHex(a.getString(R.styleable.CircleCheckbox_innerCircleColor));
                setOuterCircleColorHex(a.getString(R.styleable.CircleCheckbox_outerCircleColor));
                setCircleBorderColorHex(a.getString(R.styleable.CircleCheckbox_circleBorderColor));
                setTickThickness(a.getDimension(R.styleable.CircleCheckbox_tickThickness, tickThickness));
                setBorderThickness(a.getDimension(R.styleable.CircleCheckbox_borderThickness, borderThickness));
                setTextLeftPadding(a.getDimension(R.styleable.CircleCheckbox_labelLeftPadding, textLeftPadding));
                setTextSize(a.getDimension(R.styleable.CircleCheckbox_labelSize, textSize));
                setInnerCircleRadius(a.getDimension(R.styleable.CircleCheckbox_innerCircleRadius, innerCircleRadius));
                setOuterCircleRadius(a.getDimension(R.styleable.CircleCheckbox_outerCircleRadius, outerCircleRadius));
                setText(a.getString(R.styleable.CircleCheckbox_label));
            } finally {
                a.recycle();
            }
        }

        mPaintOuter.setColor(outerCircleColor);
        mPaintPageFill.setColor(innerCircleColor);
        mPaintTick.setColor(tick_color);
        mPaintTick.setStrokeWidth(tickThickness * 2);

        mPaintPageStroke.setColor(circleBorderColor);
        mPaintPageStroke.setStrokeWidth(borderThickness);
        mPaintPageStroke.setStyle(Paint.Style.STROKE);

        mPaintText.setTextSize(textSize);
        mPaintText.setColor(text_color);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setChecked(!isChecked);
            }
        });
    }

    private float current_radius = 0.0f;
    float time = 0;

    float tick_x = 0;
    float tick_y = 0;
    float tick_x_two = 0;
    float tick_y_two = 0;
    // Interpolator interpolator = new BounceInterpolator();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        centerX = innerCircleRadius + outerCircleRadius + getPaddingLeft();

        centerY = getHeight() / 2;

        //float interpolation = Math.abs(interpolator.getInterpolation(time));

        canvas.drawCircle(centerX, centerY, innerCircleRadius, mPaintPageStroke);

        if (isChecked) {
            if (draw_tick) {
                float tick_offset = tickThickness * 2;
                if (showOuterCircle) {
                    canvas.drawCircle(centerX, centerY, current_radius + outerCircleRadius, mPaintOuter);
                }
                canvas.drawCircle(centerX, centerY, innerCircleRadius, mPaintPageFill);
                if (draw_tick_part_one) {

                    canvas.drawCircle(centerX - tick_offset - tick_third_, centerY, tickThickness, mPaintTick);
                    canvas.drawLine(centerX - tick_offset - tick_third_, centerY, tick_x - tick_offset, tick_y, mPaintTick);
                    canvas.drawCircle(tick_x - tick_offset, tick_y, tickThickness, mPaintTick);
                } else {
                    canvas.drawCircle(centerX - tick_offset - tick_third_, centerY, tickThickness, mPaintTick);
                    canvas.drawLine(centerX - tick_offset - tick_third_, centerY, tick_x - tick_offset, tick_y, mPaintTick);
                    canvas.drawCircle(tick_x - tick_offset, tick_y, tickThickness, mPaintTick);

                    canvas.drawLine(centerX - tick_offset, tick_y, tick_x_two - tick_offset, tick_y_two, mPaintTick);
                    canvas.drawCircle(tick_x_two - tick_offset, tick_y_two, tickThickness, mPaintTick);
                }

            } else {
                if (showOuterCircle && current_radius >= innerCircleRadius - outerCircleRadius) {
                    canvas.drawCircle(centerX, centerY, (current_radius + outerCircleRadius), mPaintOuter);
                }
                canvas.drawCircle(centerX, centerY, current_radius, mPaintPageFill);
            }
        } else {
            if (!firstRun) {
                canvas.drawCircle(centerX, centerY, current_radius, mPaintPageFill);
            }
        }

        if (isChecked) {
            if (!timer_running) {
                tick_x = centerX;// tick_third_;
                tick_y = centerY + tick_third_;
                float tick_offset = tickThickness * 2;
                canvas.drawCircle(centerX - tick_offset - tick_third_, centerY, tickThickness, mPaintTick);
                canvas.drawLine(centerX - tick_offset - tick_third_, centerY, tick_x - tick_offset, tick_y, mPaintTick);
                canvas.drawCircle(tick_x - tick_offset, tick_y, tickThickness, mPaintTick);

                tick_x_two = tick_x + (tick_third_ * 1.7f);
                tick_y_two = tick_y - (tick_third_ * 1.7f);
                canvas.drawLine(centerX - tick_offset, tick_y, tick_x_two - tick_offset, tick_y_two, mPaintTick);
                canvas.drawCircle(tick_x_two - tick_offset, tick_y_two, tickThickness, mPaintTick);
                tick_x = 0;
                tick_y = 0;
                tick_x_two = 0;
                tick_x_two = 0;
            }
        }

        canvas.drawText(text, centerX + textLeftPadding + innerCircleRadius + outerCircleRadius, centerY + textSize / 2, mPaintText);
        firstRun = false;
    }

    Handler handler = new Handler();

    private void startAnimationTimer() {
        this.post(new Runnable() {
            @Override
            public void run() {
                runAnimation();
            }
        });
    }

    private void runAnimation() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                timer_running = true;
                time += increment;
                if (time < total_time) {

                    float inc = innerCircleRadius / (total_time / increment);
                    if (isChecked) {
                        current_radius = current_radius + inc;
                    } else {
                        current_radius = current_radius - inc;
                    }
                    postInvalidate();
                    runAnimation();
                } else {
                    if (isChecked) {
                        time = 0;
                        startTickAnimation();
                    } else {
                        timer_running = false;
                    }
                }
            }
        }, (long) increment);
    }

    private void startTickAnimation() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                draw_tick_part_one = true;
                timer_running = true;
                draw_tick = true;

                if (time == 0) {
                    tick_x = centerX - tick_third_;
                    tick_y = centerY;
                }
                float inc_tick = tick_third_ / (total_time / increment);

                tick_x += inc_tick;
                tick_y += inc_tick;

                time += increment;
                if (time <= total_time) {
                    postInvalidate();
                    startTickAnimation();
                } else {
                    draw_tick_part_one = false;
                    time = 0;
                    startTickPartTwoAnimation();
                }
            }
        }, (int) increment);
    }

    private void startTickPartTwoAnimation() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                timer_running = true;
                draw_tick = true;
                if (time == 0) {
                    tick_x_two = tick_x;
                    tick_y_two = tick_y;
                }

                float  inc_tick = tick_third_ * 1.7f / (total_time / increment);

                tick_x_two += inc_tick;
                tick_y_two -= inc_tick;

                time += increment;
                if (time <= total_time) {
                    postInvalidate();
                    startTickPartTwoAnimation();
                } else {
                    timer_running = false;
                    draw_tick = false;
                    time = 0;
                }
            }
        }, (long) increment);
    }

    public int getTickColor() {
        return tick_color;
    }

    public void setTickColor(int tick_color) {
        this.tick_color = tick_color;
    }

    public void setTickColorHex(String tick_color) {
        if (tick_color != null)
            this.tick_color = Color.parseColor(tick_color);
    }

    public int getTextColor() {
        return text_color;
    }

    public void setTextColor(int text_color) {
        this.text_color = text_color;
    }

    public void setTextColorHex(String color) {
        if (color != null)
            this.text_color = Color.parseColor(color);
    }

    public boolean isShowOuterCircle() {
        return showOuterCircle;
    }

    public void setShowOuterCircle(boolean showOuterCircle) {
        this.showOuterCircle = showOuterCircle;
    }

    public int getInnerCircleColor() {
        return innerCircleColor;
    }

    public void setInnerCircleColor(int innerCircleColor) {
        this.innerCircleColor = innerCircleColor;
    }

    public void setInnerCircleColorHex(String innerCircleColor) {
        if (innerCircleColor != null)
            this.innerCircleColor = Color.parseColor(innerCircleColor);
    }

    public int getOuterCircleColor() {
        return outerCircleColor;
    }

    public void setOuterCircleColor(int outerCircleColor) {
        this.outerCircleColor = outerCircleColor;
    }

    public void setOuterCircleColorHex(String outerCircleColor) {
        if (outerCircleColor != null)
            this.outerCircleColor = Color.parseColor(outerCircleColor);
    }

    public int getCircleBorderColor() {
        return circleBorderColor;
    }

    public void setCircleBorderColor(int circleBorderColor) {
        this.circleBorderColor = circleBorderColor;
    }

    public void setCircleBorderColorHex(String color) {
        if (color != null)
            this.circleBorderColor = Color.parseColor(color);
    }

    public float getTickThickness() {
        return tickThickness;
    }

    public void setTickThickness(float tickThickness) {
        this.tickThickness = tickThickness;
    }

    public float getBorderThickness() {
        return borderThickness;
    }

    public void setBorderThickness(float borderThickness) {
        this.borderThickness = borderThickness;
    }

    public float getTextLeftPadding() {
        return textLeftPadding;
    }

    public void setTextLeftPadding(float textLeftPadding) {
        this.textLeftPadding = textLeftPadding;
    }

    public float getTextSize() {
        return textSize;
    }

    public void setTextSize(float textSize) {
        this.textSize = textSize;
    }

    public float getInnerCircleRadius() {
        return innerCircleRadius;
    }

    public void setInnerCircleRadius(float innerCircleRadius) {
        this.innerCircleRadius = innerCircleRadius;
    }

    public float getOuterCircleRadius() {
        return outerCircleRadius;
    }

    public void setOuterCircleRadius(float outerCircleRadius) {
        this.outerCircleRadius = outerCircleRadius;
    }

    public void setChecked(boolean isChecked) {
        if (!timer_running) {
            this.isChecked = isChecked;
            if (listener != null)
                listener.onCheckedChanged(this, isChecked);
            if (isChecked) {
                tick_x = 0;
                tick_y = 0;
                tick_x_two = 0;
                tick_y_two = 0;
                current_radius = 0;
            }
            time = 0;
            startAnimationTimer();
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    int position;

    public void setPosition(int position) {
        this.position = position;
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CircleCheckBox view, boolean isChecked);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        if (text != null) {
            this.text = text;
        }
    }
}
