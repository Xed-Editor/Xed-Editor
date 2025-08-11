package com.rk

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.HorizontalScrollView

class SafeHorizontalScrollView : HorizontalScrollView {

    // Store the creation stack trace
    private val creationStackTrace: Array<StackTraceElement> = Throwable().stackTrace

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    override fun onRestoreInstanceState(state: Parcelable?) {
        // Print the stored stack trace
        android.util.Log.e(
            "SafeHorizontalScrollView",
            "onRestoreInstanceState called — View was created at:",
        )
        creationStackTrace.forEach {
            android.util.Log.e("SafeHorizontalScrollView", it.toString())
        }

        super.onRestoreInstanceState(state)
    }
}
