package com.rk.xededitor.MainActivity

import android.content.ClipDescription
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils.dpToPx

class ActionPopup(val context:Context) {
    private val dialogBuilder: MaterialAlertDialogBuilder
    private var dialog:AlertDialog? = null
    private val scrollView = ScrollView(context)
    private val rootView:LinearLayout
    private val typedValue = TypedValue()


    init {

       dialogBuilder = MaterialAlertDialogBuilder(context).setView(scrollView)
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        rootView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0,dpToPx(10f,context),0,0)
            scrollView.addView(this)
        }

    }

    fun setTitle(title:String) : ActionPopup{
        dialogBuilder.setTitle(title)
        return this
    }

    fun getDialogBuilder() : MaterialAlertDialogBuilder{
        return dialogBuilder
    }

    fun show() : AlertDialog?{
        dialog = dialogBuilder.show()
        return dialog
    }

    fun hide() : ActionPopup{
        dialog?.hide()
        return this
    }


    fun addItem(title:String?,description:String?,icon:Drawable?,listener: View.OnClickListener?,viewid:Int){
        fun Int.toPx(): Int = (this * context.resources.displayMetrics.density).toInt()

        val itemView = LinearLayout(context).apply {
            id = viewid
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL




            setBackgroundResource(typedValue.resourceId)
            isClickable = true
            isFocusable = true
            setPadding(20.toPx(), 20.toPx(), 20.toPx(), 20.toPx())
        }

        val imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 20.toPx()
            }
            setImageDrawable(icon)
        }

        val textLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val titleTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = title
            setTypeface(null, Typeface.BOLD)
        }

        val subtitleTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = description
        }

        textLayout.addView(titleTextView)
        textLayout.addView(subtitleTextView)

        itemView.addView(imageView)
        itemView.addView(textLayout)
        itemView.setOnClickListener(listener)
        rootView.addView(itemView)
    }
}