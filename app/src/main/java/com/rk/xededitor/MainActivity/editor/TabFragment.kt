package com.rk.xededitor.MainActivity.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class TabFragment : Fragment() {

    var file: File? = null
    var editor: KarbonEditor? = null
    
    /**
     * See {@link com.rk.xededitor.MainActivity.handlers.MenuItemHandler} for more information.
     */
    var setListener = false
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val context = requireContext()
            arguments?.let {
                val filePath = it.getString(ARG_FILE_PATH)
                if (filePath != null) {
                    file = File(filePath)
                }
            }
            editor = KarbonEditor(context)
            
            val setupEditor = SetupEditor(editor!!, context)
            setupEditor.ensureTextmateTheme(context)
            lifecycleScope.launch(Dispatchers.Default) { setupEditor.setupLanguage(file!!.name) }
            lifecycleScope.launch { editor!!.loadFile(file!!) }
            
        } catch (e: Exception) {
            e.printStackTrace()
            editor?.release()
            editor = null
        }
    }

    fun save(showToast: Boolean = true) {
        lifecycleScope.launch(Dispatchers.IO) {
            editor?.saveToFile(file!!)
            try {
                MainActivity.activityRef.get()?.let { activity ->
                    val index = activity.tabViewModel.fragmentFiles.indexOf(file)
                    activity.tabViewModel.fragmentTitles.let {
                        if (file!!.name != it[index]) {
                            it[index] = file!!.name
                            withContext(Dispatchers.Main) {
                                activity.tabLayout.getTabAt(index)?.text = file!!.name
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { rkUtils.toast(e.message) }
            }
            if (showToast) {
                withContext(Dispatchers.Main) { rkUtils.toast(rkUtils.getString(R.string.saved)) }
            }
        }
    }

    fun undo() {
        editor?.undo()
        MainActivity.activityRef.get()?.let {
            it.menu.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
            it.menu.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
        }
    }

    fun redo() {
        editor?.redo()
        MainActivity.activityRef.get()?.let {
            it.menu.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
            it.menu.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return editor
    }

    override fun onDestroyView() {
        super.onDestroyView()
        editor?.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        editor?.release()
    }

    companion object {
        private const val ARG_FILE_PATH = "file_path"

        fun newInstance(file: File): TabFragment {
            val fragment = TabFragment()
            val args = Bundle().apply { putString(ARG_FILE_PATH, file.absolutePath) }
            fragment.arguments = args
            return fragment
        }
    }
}
