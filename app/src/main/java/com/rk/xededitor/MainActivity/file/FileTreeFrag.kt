package com.rk.xededitor.MainActivity.file

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node
import com.rk.filetree.widget.FileTree
import com.rk.libcommons.After
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import java.io.File

@Suppress("DEPRECATION")
class FileTreeFrag : Fragment() {

    private lateinit var scrollView:ViewGroup
    private lateinit var rootFile:FileObject
    lateinit var fileTree: FileTree

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootFile = (requireArguments().getSerializable("file") as? FileObject)!!

        fileTree = FileTree(requireContext())
        fileTree.loadFiles(rootFile)
        fileTree.setOnFileClickListener(fileClickListener)
        fileTree.setOnFileLongClickListener(fileLongClickListener)
        scrollView = FileTreeScrollViewManager.getFileTreeParentScrollView(requireContext(), fileTree)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return scrollView
    }

    companion object {
        fun newInstance(file:FileObject): FileTreeFrag {
            val fragment = FileTreeFrag()
            val args = Bundle().apply {
                putSerializable("file", file)
            }
            fragment.arguments = args
            return fragment
        }

        val fileClickListener = object : FileClickListener {
            override fun onClick(node: Node<FileObject>) {
                if (node.value.isDirectory()) {
                    return
                }

                BaseActivity.getActivity(MainActivity::class.java)?.let {
                    val loading = LoadingPopup(it, null).show()
                    val file = File(node.value.getAbsolutePath())

                    //delay 100ms for smoother click
                    //opening a file always take more than 500ms because of these delays
                    After(100) {
                        rkUtils.runOnUiThread {
                            it.newEditor(file)
                            it.adapter?.onNewEditor(file)
                        }

                        //delay close drawer after 400ms
                        After(400) {
                            if (!SettingsData.getBoolean(Keys.KEEP_DRAWER_LOCKED, false)) {
                                rkUtils.runOnUiThread {
                                    it.binding.drawerLayout.close()
                                    loading.hide()
                                }
                            }
                        }
                    }
                }
            }
        }

        val fileLongClickListener = object : FileLongClickListener {
            override fun onLongClick(node: Node<FileObject>) {
                BaseActivity.getActivity(MainActivity::class.java)?.apply {
                    ProjectManager.getSelectedProjectRootFilePath()?.let {
                        FileAction(this, File(it), File(node.value.getAbsolutePath()))
                    }

                }
            }

        }



    }
}