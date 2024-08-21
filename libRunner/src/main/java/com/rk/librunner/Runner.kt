package com.rk.librunner

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
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.librunner.beanshell.BeanshellRunner
import com.rk.librunner.markdown.MarkDown
import java.io.File

interface RunnableInterface {
    fun run(file: File, context:Context)
    fun getName() : String
    fun getDescription():String
    fun getIcon(context: Context): Drawable?
}

object Runner {

    private val registry = HashMap<String,List<RunnableInterface>>()

    init {
        registry["bsh"] = arrayListOf(BeanshellRunner())
        registry["md"] = arrayListOf(MarkDown())
    }

    fun isRunnable(file:File) : Boolean{
        val ext = file.name.substringAfterLast('.', "")
        return registry.keys.any { it == ext }
    }

    fun run(file: File,context:Context){
        if (isRunnable(file)){
            val ext = file.name.substringAfterLast('.', "")
            val runners = registry[ext]
            if (runners?.size == 1){
                runners[0].run(file,context)
            }else{
                val scrollView = ScrollView(context)
                runners?.forEach { runner ->
                    LinearLayout(context).apply {
                        id = View.generateViewId()
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        val typedValue = TypedValue()
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                        background = ContextCompat.getDrawable(context, typedValue.resourceId)


                        isClickable = true
                        isFocusable = true
                        gravity = Gravity.CENTER_VERTICAL
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(20, 20, 20, 20)


                        val imageView = ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginEnd = 20
                            }
                            setImageDrawable(runner.getIcon(context) ?: ContextCompat.getDrawable(context, R.drawable.settings))
                        }

                        // LinearLayout for the TextViews
                        val textContainer = LinearLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            orientation = LinearLayout.VERTICAL

                            // First TextView
                            val titleTextView = TextView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                text = runner.getName()
                                setTypeface(null, Typeface.BOLD)
                            }

                            // Second TextView
                            val subtitleTextView = TextView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                text = runner.getDescription()
                            }

                            addView(titleTextView)
                            addView(subtitleTextView)
                        }

                        addView(imageView)
                        addView(textContainer)
                        scrollView.addView(this)
                        setOnClickListener {
                            runner.run(file,context)
                        }
                    }


                }


                MaterialAlertDialogBuilder(context).setTitle("Choose Runtime").setView(scrollView).setNegativeButton("Cancel",null).show()

            }
        }
    }
}