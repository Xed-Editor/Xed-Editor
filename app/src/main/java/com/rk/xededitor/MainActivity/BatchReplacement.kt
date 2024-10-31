package com.rk.xededitor.MainActivity

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.R
import com.rk.xededitor.SimpleEditor.SimpleEditor
import com.rk.xededitor.databinding.ActivityBatchReplacementBinding
import com.rk.xededitor.rkUtils.dpToPx
import com.rk.xededitor.rkUtils.toast

/*
 *
 * DO NOT TRANSLATE
 *
 * */

class BatchReplacement : BaseActivity() {
    private lateinit var binding: ActivityBatchReplacementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBatchReplacementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = resources.getString(R.string.title_activity_batch_replacement)
    }

    private fun newEditBox(hint: String): View {
        val rootLinearLayout = LinearLayout(this)
        rootLinearLayout.layoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        rootLinearLayout.tag = "keyRep"
        rootLinearLayout.orientation = LinearLayout.VERTICAL

        // Create the inner LinearLayout
        val innerLinearLayout = LinearLayout(this)
        val innerParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(50f, this),
            ) // height is 50dp
        innerParams.setMargins(dpToPx(22f, this), dpToPx(10f, this), dpToPx(22f, this), 0)
        innerLinearLayout.layoutParams = innerParams
        innerLinearLayout.tag = "keyword"

        // Set the background for innerLinearLayout
        val drawable = ContextCompat.getDrawable(this, R.drawable.edittext)
        innerLinearLayout.background = drawable

        // Create the EditText
        val editText = EditText(this)
        val editTextParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        editText.layoutParams = editTextParams
        editText.setPadding(
            dpToPx(8f, this),
            0,
            dpToPx(5f, this),
            0,
        ) // paddingStart 8dp, paddingEnd 5dp
        editText.id = View.generateViewId()
        editText.isSingleLine = true
        editText.hint = hint
        editText.setBackgroundColor(Color.TRANSPARENT)

        // Add EditText to inner LinearLayout
        innerLinearLayout.addView(editText)

        rootLinearLayout.addView(innerLinearLayout)
        return rootLinearLayout
    }

    fun addBatch(v: View?) {
        (findViewById<View>(R.id.mainBody) as LinearLayout).addView(newEditBox("keyword (Regex)"))
        (findViewById<View>(R.id.mainBody) as LinearLayout).addView(newEditBox("replacement"))
        val view = View(this)

        // Set the width and height of the View
        val params =
            LinearLayout.LayoutParams(
                dpToPx(0f, this), // Width in dp
                dpToPx(20f, this), // Height in dp
            )
        view.layoutParams = params
        (findViewById<View>(R.id.mainBody) as LinearLayout).addView(view)

        findViewById<View>(R.id.removeBatch).visibility = View.VISIBLE
    }

    fun removeBatch(v: View?) {
        val linearLayout = findViewById<LinearLayout>(R.id.mainBody)
        val childCount = linearLayout.childCount
        if (childCount > 3) {
            // Remove the last child first
            linearLayout.removeViewAt(childCount - 1)
            // Remove the second-to-last child
            linearLayout.removeViewAt(childCount - 2)
            // Remove the third-to-last child
            linearLayout.removeViewAt(childCount - 3)
            if (linearLayout.childCount <= 3) {
                findViewById<View>(R.id.removeBatch).visibility = View.GONE
            }
        } else {
            findViewById<View>(R.id.removeBatch).visibility = View.GONE
        }
    }

    fun replaceAll(v: View?) {
        LoadingPopup(this, 100L)
        val linearLayout = findViewById<LinearLayout>(R.id.mainBody)
        for (i in 0 until linearLayout.childCount) {
            val view = linearLayout.getChildAt(i)
            if (view is LinearLayout) {
                val l = view.getChildAt(0) as LinearLayout
                val editText = l.getChildAt(0) as EditText
                if (editText.hint == "keyword (Regex)") {
                    val viewx = linearLayout.getChildAt(i + 1)
                    val lx = (viewx as LinearLayout).getChildAt(0) as LinearLayout
                    val editTextx = lx.getChildAt(0) as EditText
                    val keyword = editText.text.toString()
                    val replacement = editTextx.text.toString()

                    if (MainActivity.activityRef.get() != null) {
                        MainActivity.activityRef.get()?.adapter?.getCurrentFragment()?.let {
                            if (it.type!! == FragmentType.EDITOR){
                                (it.fragment as EditorFragment).editor?.setText((it.fragment as EditorFragment).editor?.text.toString().replace(keyword, replacement))
                            }else{
                                throw RuntimeException("Unsupported Fragment type")
                            }
                        }
                    } else if (intent.extras?.getBoolean("isExt", false) == true) {
                        // if we are working with external editor
                        SimpleEditor.editor?.setText(
                            SimpleEditor.editor!!.text.toString().replace(keyword, replacement)
                        )
                    }
                }
            }
        }

        toast(getString(R.string.action_done))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here
        val id = item.itemId
        if (id == android.R.id.home) {
            // Handle the back arrow click here
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
