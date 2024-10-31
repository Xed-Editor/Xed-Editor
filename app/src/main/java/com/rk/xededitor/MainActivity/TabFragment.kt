package com.rk.xededitor.MainActivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.media.MediaFragment
import java.io.File


class TabFragment : Fragment() {
    var fragment: CoreFragment? = null
    var type: FragmentType? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        type = arguments?.getSerializable("type") as FragmentType
        
        when (type) {
            FragmentType.EDITOR -> {
                arguments?.let {
                    it.getString(ARG_FILE_PATH)?.let { filePath ->
                        val file = File(filePath)
                        val editorFragment = EditorFragment(requireContext())
                        editorFragment.onCreate()
                        fragment = editorFragment
                        editorFragment.loadFile(file)
                    }
                }
            }
            
            FragmentType.AUDIO, FragmentType.VIDEO, FragmentType.IMAGE -> {
                arguments?.let {
                    it.getString(ARG_FILE_PATH)?.let { filePath ->
                        val file = File(filePath)
                        val mediaFragment = MediaFragment(requireContext())
                        mediaFragment.onCreate()
                        fragment = mediaFragment
                        mediaFragment.loadFile(file)
                    }
                }
            }
            
            FragmentType.TERMINAL -> {}
            FragmentType.WEB -> {}
            null -> {
                throw RuntimeException("the type is null")
            }
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
            args.putSerializable("type", type)
            
            
            
            when (type) {
                FragmentType.EDITOR -> {
                    args.putString(ARG_FILE_PATH, file.absolutePath)
                }
                
                FragmentType.IMAGE, FragmentType.AUDIO, FragmentType.VIDEO -> {
                    args.putString(ARG_FILE_PATH, file.absolutePath)
                }
                
                FragmentType.TERMINAL -> {}
                FragmentType.WEB -> {}
            }
            
            fragment.arguments = args
            return fragment
        }
    }
}
