package com.rk.compose.filetree

import android.os.FileObserver
import com.rk.file_wrapper.FileWrapper
import com.rk.compose.filetree.RecursiveFileObserver.SingleFileObserver
import com.rk.libcommons.DefaultScope
import com.rk.settings.Settings
import com.rk.xededitor.MainActivity.Kee
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayList
import java.util.Stack

/**
 * Enhanced FileObserver to support recursive directory monitoring basically.
 * @author    uestc.Mobius <mobius></mobius>@toraleap.com>
 * @version  2011.0121
 */

class RecursiveFileObserver @JvmOverloads constructor(path: String?, mask: Int = ALL_EVENTS) :
    FileObserver(path, mask) {
    private var mObservers: MutableList<SingleFileObserver>? = null
    var mPath: String?
    var mMask: Int

    init {
        mPath = path
        mMask = mask
    }

    override fun startWatching() {
        if (mObservers != null) return

        mObservers = ArrayList<SingleFileObserver>()
        val stack = Stack<String>()
        stack.push(mPath)

        while (!stack.isEmpty()) {
            val parent = stack.pop()
            mObservers!!.add(SingleFileObserver(parent, mMask))
            val path = File(parent)
            val files = path.listFiles()
            if (null == files) continue
            for (f in files) {
                if (f.isDirectory && f.name != "." && f.name != "..") {
                    stack.push(f.path)
                }
            }
        }

        for (sfo in mObservers!!) {
            sfo.startWatching()
        }
    }

    override fun stopWatching() {
        if (mObservers == null) return

        for (sfo in mObservers) {
            sfo.stopWatching()
        }
        mObservers!!.clear()
        mObservers = null
    }

    var pendingUpdate = ""

    override fun onEvent(event: Int, path: String?) {
        val correctPath = if (path.toString().endsWith("/null")){
            path.toString().removeSuffix("/null")
        }else{
            path.toString()
        }

        when (event) {
            CLOSE_WRITE -> {
                if (Settings.auto_save.not()){
                    MainActivity.withContext {
                        val fragment =
                            adapter?.tabFragments?.get(
                                Kee(
                                    FileWrapper(
                                        File(correctPath)
                                    )
                                )
                            )
                                ?.get()?.fragment

                        if (fragment is EditorFragment){
                            fragment.refreshEditorContent(autoRefresher = true)
                        }
                    }
                }
                }
            CREATE -> {
                DefaultScope.launch{
                    delay(500)
                    val file = File(correctPath)
                    fileTreeViewModel?.updateCache(FileWrapper(file = file))
                }
            }

            MOVED_FROM,MOVED_TO,MOVE_SELF,DELETE,DELETE_SELF,MODIFY -> {
                DefaultScope.launch{
                    delay(500)
                    val file = File(correctPath)
                    fileTreeViewModel?.updateCache(FileWrapper(file = file))
                }
            }
        }
    }

    /**
     * Monitor single directory and dispatch all events to its parent, with full path.
     * @author    uestc.Mobius <mobius></mobius>@toraleap.com>
     * @version  2011.0121
     */
    internal inner class SingleFileObserver(path: String?, mask: Int) : FileObserver(path, mask) {
        var mPath: String?

        constructor(path: String?) : this(path, ALL_EVENTS) {
            mPath = path
        }

        init {
            mPath = path
        }

        override fun onEvent(event: Int, path: String?) {
            val newPath = "$mPath/$path"
            this@RecursiveFileObserver.onEvent(event, newPath)
        }
    }

    companion object {
        /** Only modification events  */
        var CHANGES_ONLY: Int =
            CREATE or DELETE or CLOSE_WRITE or MOVE_SELF or MOVED_FROM or MOVED_TO
    }
}