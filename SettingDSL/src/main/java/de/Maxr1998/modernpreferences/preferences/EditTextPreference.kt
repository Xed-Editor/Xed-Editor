package de.Maxr1998.modernpreferences.preferences

import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatEditText
import de.Maxr1998.modernpreferences.helpers.DISABLED_RESOURCE_ID

class EditTextPreference(key: String) : DialogPreference(key) {

    var currentInput: CharSequence? = null
        private set

    /**
     * The default value of this preference, when nothing was committed to storage yet
     */
    var defaultValue: String = ""

    /**
     * The [InputType] applied to the contained [EditText][AppCompatEditText]
     */
    var textInputType: Int = InputType.TYPE_NULL

    @StringRes
    var textInputHintRes: Int = DISABLED_RESOURCE_ID
    var textInputHint: CharSequence? = null

    var textChangeListener: OnTextChangeListener? = null

    /**
     * Allows to override the summary, providing the current input value when called.
     *
     * Summary falls back to [summary] or [summaryRes] when null is returned.
     */
    var summaryProvider: (CharSequence?) -> CharSequence? = { null }

    override fun onAttach() {
        super.onAttach()
        if (currentInput == null) {
            currentInput = getString() ?: defaultValue.takeUnless(String::isEmpty)
        }
    }

    override fun createDialog(context: Context): Dialog = Config.dialogBuilderFactory(context).apply {
        when {
            titleRes != DISABLED_RESOURCE_ID -> setTitle(titleRes)
            else -> setTitle(title)
        }
        val editText = AppCompatEditText(context).apply {
            if (textInputType != InputType.TYPE_NULL) {
                inputType = textInputType
            }
            when {
                textInputHintRes != DISABLED_RESOURCE_ID -> setHint(textInputHintRes)
                textInputHint != null -> hint = textInputHint
            }
            setText(currentInput)
        }
        val dialogContent = FrameLayout(context).apply {
            val layoutParams = ViewGroup.MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                @Suppress("MagicNumber")
                val tenDp = (10 * context.resources.displayMetrics.density).toInt()
                marginStart = 2 * tenDp
                marginEnd = 2 * tenDp
                topMargin = tenDp
            }
            addView(editText, layoutParams)
        }
        setView(dialogContent)
        setCancelable(false)
        setPositiveButton(android.R.string.ok) { _, _ ->
            editText.text?.let(::persist)
            requestRebind()
        }
        setNegativeButton(android.R.string.cancel) { _, _ ->
            editText.setText(currentInput)
        }
    }.create()

    private fun persist(input: CharSequence) {
        if (textChangeListener?.onTextChange(this, input) != false) {
            currentInput = input
            commitString(input.toString())
        }
    }

    override fun resolveSummary(context: Context): CharSequence? {
        return summaryProvider(currentInput) ?: super.resolveSummary(context)
    }

    fun interface OnTextChangeListener {
        /**
         * Notified when the value of the connected [EditTextPreference] changes,
         * meaning after the user closes the dialog by pressing "ok".
         * This is called before the change gets persisted and can be prevented by returning false.
         *
         * @param text the new value
         *
         * @return true to commit the new value to [SharedPreferences][android.content.SharedPreferences]
         */
        fun onTextChange(preference: EditTextPreference, text: CharSequence): Boolean
    }
}