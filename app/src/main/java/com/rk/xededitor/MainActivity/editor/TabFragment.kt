package com.rk.xededitor.MainActivity.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.MainActivity.editor.fragments.EditorFragment
import com.rk.xededitor.MainActivity.editor.fragments.core.CoreFragment
import com.rk.xededitor.MainActivity.editor.fragments.core.FragmentType
import com.rk.xededitor.SetupEditor
import kotlinx.coroutines.launch
import java.io.File


class TabFragment : Fragment() {
    var fragment:CoreFragment? = null
    var type:FragmentType? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        type = arguments?.getSerializable("type") as FragmentType
        
        when(type){
            FragmentType.EDITOR -> {
                arguments?.let {
                    it.getString(ARG_FILE_PATH)?.let { filePath ->
                        val file = File(filePath)
                        val editorFragment = EditorFragment(requireContext())
                        editorFragment.onCreate()
                        lifecycleScope.launch { editorFragment.editor?.loadFile(file) }
                        fragment = editorFragment
                        fragment!!.loadFile(file)
                    }
                }
            }
            FragmentType.VIDEO -> {
            
            }
            FragmentType.AUDIO -> {}
            FragmentType.IMAGE -> {}
            FragmentType.TERMINAL -> {}
            null -> {throw RuntimeException("the type is null")}
        }
        
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return fragment?.getView()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fragment?.onDestroy()
    }
    
    companion object {
        private const val ARG_FILE_PATH = "file_path"
        
        fun newInstance(file: File, type: FragmentType): TabFragment {
            val fragment = TabFragment()
            val args = Bundle()
            args.putSerializable("type",type)
            
            
            
            when (type) {
                FragmentType.EDITOR -> {
                    args.putString(ARG_FILE_PATH, file.absolutePath)
                }
                
                
                FragmentType.AUDIO -> {
                
                }
                
                FragmentType.IMAGE -> {
                
                }
                
                FragmentType.VIDEO -> {
                
                }
                
                FragmentType.TERMINAL -> {}
            }
            
            fragment.arguments = args
            return fragment
        }
    }
}
