package com.rk.xededitor.MainActivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rk.file.FileObject
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.media.ImageFragment
import com.rk.xededitor.MainActivity.tabs.media.WebFragment
import com.rk.xededitor.MainActivity.tabs.media.VideoFragment


class TabFragment : Fragment() {
    var fragment: CoreFragment? = null
    var type: FragmentType? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        type = arguments?.getSerializable("type") as FragmentType
        
        when (type) {
            FragmentType.EDITOR -> {
                arguments?.let {
                    it.getSerializable(ARG_FILE_PATH)?.let { file ->
                        val editorFragment = EditorFragment(requireContext())
                        editorFragment.onCreate()
                        fragment = editorFragment
                        editorFragment.loadFile(file as com.rk.file.FileObject)
                    }

                }
            }
            
            FragmentType.AUDIO -> {
                arguments?.let {
                    it.getSerializable(ARG_FILE_PATH)?.let { file ->
                        val mediaFragment = WebFragment(requireContext())
                        mediaFragment.onCreate()
                        fragment = mediaFragment
                        mediaFragment.loadFile(file as com.rk.file.FileObject)
                    }
                }
            }
            
            FragmentType.IMAGE -> {
                arguments?.let {
                    it.getSerializable(ARG_FILE_PATH)?.let { file ->
                        val imageFragment = ImageFragment(requireContext())
                        imageFragment.onCreate()
                        fragment = imageFragment
                        imageFragment.loadFile(file as com.rk.file.FileObject)
                    }
                }
            }
            
            FragmentType.VIDEO -> {
                arguments?.let {
                    it.getSerializable(ARG_FILE_PATH)?.let { file ->
                        val videoFragment = VideoFragment(requireContext())
                        videoFragment.onCreate()
                        fragment = videoFragment
                        videoFragment.loadFile(file as com.rk.file.FileObject)
                    }
                }
            }
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
        return fragment?.getView().also { it?.isFocusableInTouchMode = true;it?.requestFocus();it?.requestFocusFromTouch() }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fragment?.onDestroy()
    }
    
    companion object {
        private const val ARG_FILE_PATH = "file_path"
        
        fun newInstance(file: com.rk.file.FileObject, type: FragmentType): TabFragment {
            val fragment = TabFragment()
            val args = Bundle()
            args.putSerializable("type", type)
            

            when (type) {
                FragmentType.EDITOR -> {
                    args.putSerializable(ARG_FILE_PATH,file)
                }
                
                FragmentType.IMAGE, FragmentType.AUDIO, FragmentType.VIDEO -> {
                    args.putSerializable(ARG_FILE_PATH,file)
                }
            }
            fragment.arguments = args
            return fragment
        }
    }
}
