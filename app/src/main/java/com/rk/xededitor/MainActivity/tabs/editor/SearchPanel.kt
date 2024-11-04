package com.rk.xededitor.MainActivity.tabs.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import com.rk.xededitor.R

class SearchPanel(val root: ViewGroup){
    val view:LinearLayout = LayoutInflater.from(root.context).inflate(R.layout.search_layout, root, false) as LinearLayout
    
    
}