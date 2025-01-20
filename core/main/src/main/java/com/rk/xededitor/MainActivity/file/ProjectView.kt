package com.rk.xededitor.MainActivity.file

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.navigationrail.NavigationRailView

class ProjectView : NavigationRailView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)


    override fun getMaxItemCount(): Int {
        return 11
    }
}