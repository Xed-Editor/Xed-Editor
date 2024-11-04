package com.rk.xededitor.MainActivity.tabs.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import com.rk.xededitor.R
import io.github.rosemoe.sora.widget.EditorSearcher
import java.util.regex.PatternSyntaxException

class SearchPanel(val root: ViewGroup, editor: KarbonEditor) {
    val view: LinearLayout = LayoutInflater.from(root.context).inflate(R.layout.search_layout, root, false) as LinearLayout
    
    
    init {
        val searcher = editor.searcher
        var isSearching = false
        
        view.findViewById<Button>(R.id.close).setOnClickListener { isSearching = false;searcher.stopSearch();view.visibility = View.GONE }
        
        val searchEditText = view.findViewById<EditText>(R.id.search_editor)
        val replaceEditText = view.findViewById<EditText>(R.id.replace_editor)
        
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
                } catch (e: PatternSyntaxException) {
                    // Regex error
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
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryCommitSearch()
                true
            } else {
                false
            }
        }
        
       /* view.findViewById<ImageView>(R.id.menu).setOnClickListener {
            val popupMenu = PopupMenu(editor.context, it).apply {
                menu.add(0, 1, 0, "Item 1") // groupId, itemId, order, title
                menu.add(0, 2, 1, "Item 2")
                menu.add(0, 3, 2, "Item 3")
                
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> {
                            // Handle Item 1 click
                            true
                        }
                        
                        2 -> {
                            // Handle Item 2 click
                            true
                        }
                        
                        3 -> {
                            // Handle Item 3 click
                            true
                        }
                        
                        else -> false
                    }
                }
                
                show()
                
            }
            
        }*/
    }
    
    
    private fun getSearchOptions(): EditorSearcher.SearchOptions {
        val caseInsensitive = true
        var type = EditorSearcher.SearchOptions.TYPE_NORMAL
        val regex = false
        if (regex) {
            type = EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
        }
        val wholeWord = false
        if (wholeWord) {
            type = EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
        }
        return EditorSearcher.SearchOptions(type, caseInsensitive)
    }
}