package com.rk.xededitor.MainActivity

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import kotlin.properties.Delegates

class MultiView : LinearLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    
    private lateinit var views:MutableMap<Int,View>
    private var selectedViewId by Delegates.notNull<Int>()
    
    fun setViews(children: MutableMap<Int,View>){
        views = children
    }
    
    fun switchTo(id:Int){
        if (views.containsKey(id).not()){
            throw Exception("no associated view with $id")
        }
        
        clearView()
        super.addView(views[id])
        selectedViewId = id
    }
    
    fun getCurrentViewId() = selectedViewId
    
    fun clearView(){
        for (i in 0 until childCount){
            removeView(getChildAt(i))
        }
    }
    
    override fun addView(child: View) {
        val id = View.generateViewId()
        views[id] = child
        switchTo(id)
    }
    
}