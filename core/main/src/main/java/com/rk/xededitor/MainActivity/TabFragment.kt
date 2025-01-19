package com.rk.xededitor.MainActivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rk.file.FileObject
import com.rk.libcommons.DefaultScope
import com.rk.xededitor.MainActivity.file.getFragmentType
import com.rk.xededitor.MainActivity.handlers.updateMenu
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.media.ImageFragment
import com.rk.xededitor.MainActivity.tabs.media.WebFragment
import com.rk.xededitor.MainActivity.tabs.media.VideoFragment
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.launch

class TabFragment : Fragment() {
    var fragment: CoreFragment? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = arguments?.getSerializable(ARG_FILE_PATH) as FileObject

        if (tabs.isNotEmpty()){
            for (builder in tabs.values){
                val builtFragment = builder.invoke(file,this)
                if (builtFragment != null){
                    fragment = builtFragment
                    builtFragment.onCreate()
                    builtFragment.loadFile(file)
                    break
                }
            }
        }

        if (fragment == null){
            when (file.getFragmentType()) {
                FragmentType.EDITOR -> {

                   
                    val editorFragment = EditorFragment(requireContext())
                    editorFragment.onCreate()
                    fragment = editorFragment
                    editorFragment.loadFile(file)
                }

                FragmentType.AUDIO -> {
                    val mediaFragment = WebFragment(requireContext())
                    mediaFragment.onCreate()
                    fragment = mediaFragment
                    mediaFragment.loadFile(file)
                }

                FragmentType.IMAGE -> {
                    val imageFragment = ImageFragment(requireContext())
                    imageFragment.onCreate()
                    fragment = imageFragment
                    imageFragment.loadFile(file)
                }

                FragmentType.VIDEO -> {
                    val videoFragment = VideoFragment(requireContext())
                    videoFragment.onCreate()
                    fragment = videoFragment
                    videoFragment.loadFile(file)
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
        return fragment?.getView().also { it?.isFocusableInTouchMode = true;it?.requestFocus();it?.requestFocusFromTouch() }
    }
    
    override fun onDestroy() {
        fragment?.onDestroy()
        super.onDestroy()
        DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }
    }
    
    companion object {
        //id : builder
        val tabs:HashMap<String,(file:FileObject,TabFragment)->CoreFragment?> = hashMapOf()

        //return null if the tab should be handled by xed
        fun registerFileTab(id:String,builder:(file:FileObject,TabFragment)->CoreFragment?){
            tabs[id] = builder
        }

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
