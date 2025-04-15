package com.rk.xededitor.MainActivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rk.extension.Hooks
import com.rk.file_wrapper.FileObject
import com.rk.libcommons.DefaultScope
import com.rk.xededitor.MainActivity.file.getFragmentType
import com.rk.xededitor.MainActivity.handlers.updateMenu
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.media.ImageFragment
import com.rk.xededitor.MainActivity.tabs.media.WebFragment
import com.rk.xededitor.MainActivity.tabs.media.VideoFragment
import kotlinx.coroutines.launch

class TabFragment : Fragment() {
    var fragment: CoreFragment? = null
    private var file:FileObject? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = arguments?.getSerializable(ARG_FILE_PATH) as FileObject

        if (Hooks.Editor.tabs.isNotEmpty()){
            for (builder in Hooks.Editor.tabs.values){
                val builtFragment = builder.invoke(file!!,this)
                if (builtFragment != null){
                    fragment = builtFragment
                    break
                }
            }
        }

        if (fragment == null){
            when (file!!.getFragmentType()) {
                FragmentType.EDITOR -> {
                    val editorFragment = EditorFragment(requireContext(),lifecycleScope)
                    fragment = editorFragment
                }

                FragmentType.AUDIO -> {
                    val mediaFragment = WebFragment(requireContext(),lifecycleScope)
                    fragment = mediaFragment
                }

                FragmentType.IMAGE -> {
                    val imageFragment = ImageFragment(requireContext())
                    fragment = imageFragment
                }

                FragmentType.VIDEO -> {
                    val videoFragment = VideoFragment(requireContext())
                    fragment = videoFragment
                }
            }
        }



        DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }

    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        fragment!!.onCreate()
        fragment!!.loadFile(file = file!!)
        return fragment?.getView()?.also { it.isFocusableInTouchMode = true;it.requestFocus();it.requestFocusFromTouch(); }
    }
    
    override fun onDestroy() {
        fragment?.onDestroy()
        super.onDestroy()
        DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }
    }
    
    companion object {
        private const val ARG_FILE_PATH = "file_path"
        
        fun newInstance(file: FileObject): TabFragment {
            val fragment = TabFragment()
            val args = Bundle()
            args.putSerializable(ARG_FILE_PATH,file)

            fragment.arguments = args
            return fragment
        }
    }
}
