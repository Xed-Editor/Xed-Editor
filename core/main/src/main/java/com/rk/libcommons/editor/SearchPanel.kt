package com.rk.libcommons.editor

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import com.rk.xededitor.R
import io.github.rosemoe.sora.widget.EditorSearcher
import java.util.regex.PatternSyntaxException

class SearchPanel(val root: ViewGroup, editor: KarbonEditor) {
    val view: LinearLayout = LayoutInflater.from(root.context).inflate(R.layout.search_layout, root, false) as LinearLayout
    
    private var ignoreCase = true
    private var searchWholeWord = false
    private var searchRegex = false
    
    
    init {
        val searcher = editor.searcher
        var isSearching = false
        
        view.findViewById<ImageButton>(R.id.close).setOnClickListener { isSearching = false;searcher.stopSearch();view.visibility = View.GONE }
        
        val searchEditText = view.findViewById<EditText>(R.id.search_editor)
        val replaceEditText = view.findViewById<EditText>(R.id.replace_editor)
        val defaultColor = searchEditText.textColors.defaultColor

        view.findViewById<Button>(R.id.btn_replace).setOnClickListener {
            if (isSearching) {
                searcher.replaceCurrentMatch(replaceEditText.text.toString())
            }
            
        }
        view.findViewById<Button>(R.id.btn_replace_all).setOnClickListener {
            if (isSearching) {
                searcher.replaceAll(replaceEditText.text.toString())
            }
        }
        view.findViewById<Button>(R.id.btn_goto_next).setOnClickListener {
            if (isSearching) {
                searcher.gotoNext()
            }
        }
        view.findViewById<Button>(R.id.btn_goto_prev).setOnClickListener {
            if (isSearching) {
                searcher.gotoPrevious()
            }
        }
        
        fun tryCommitSearch() {
            val query = searchEditText.editableText
            if (query.isNotEmpty()) {
                try {
                    searcher.search(
                        query.toString(),
                        getSearchOptions()
                    )
                    isSearching = true
                    Handler(Looper.getMainLooper()).post {
                        searchEditText.setTextColor(defaultColor)
                    }
                } catch (e: PatternSyntaxException) {
                    Handler(Looper.getMainLooper()).post {
                        searchEditText.setTextColor(Color.RED)
                    }
                }
            } else {
                searcher.stopSearch()
            }
        }
        
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                tryCommitSearch()
            }
        })
        
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_NEXT) {
                tryCommitSearch()
                true
            } else {
                false
            }
        }
        
        
        
        
        view.findViewById<ImageView>(R.id.menu).setOnClickListener {
            
            val popupMenu = PopupMenu(editor.context, it)
            popupMenu.menu.add(0, 0, 0, "Ignore Case").apply {
                isCheckable = true
                isChecked = ignoreCase
            }
            popupMenu.menu.add(0, 1, 1, "Regex").apply {
                isCheckable = true
                isChecked = searchRegex
            }
            popupMenu.menu.add(0, 2, 2, "Whole word").apply {
                isCheckable = true
                isChecked = searchWholeWord
            }
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    0 -> {
                        menuItem.isChecked = menuItem.isChecked.not()
                        ignoreCase = menuItem.isChecked
                        true
                    }
                    
                    1 -> {
                        menuItem.isChecked = menuItem.isChecked.not()
                        searchRegex = menuItem.isChecked
                        true
                    }
                    
                    2 -> {
                        menuItem.isChecked = menuItem.isChecked.not()
                        searchWholeWord = menuItem.isChecked
                        true
                    }
                    
                    else -> false
                }
            }
            
            popupMenu.show()
            
        }
    }
    
    
    private fun getSearchOptions(): EditorSearcher.SearchOptions {
        val caseInsensitive = ignoreCase
        var type = EditorSearcher.SearchOptions.TYPE_NORMAL
        val regex = searchRegex
        if (regex) {
            type = EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
        }
        val wholeWord = searchWholeWord
        if (wholeWord) {
            type = EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
        }
        return EditorSearcher.SearchOptions(type, caseInsensitive)
    }
}