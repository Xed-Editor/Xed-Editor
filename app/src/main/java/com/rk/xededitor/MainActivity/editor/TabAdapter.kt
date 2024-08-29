package com.rk.xededitor.MainActivity.editor

import android.os.Parcelable
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.rk.xededitor.BaseActivity.Companion.getActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler.updateMenuItems
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.MainActivity.StaticData.fileSet
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

class TabAdapter(private val fragmentManager: FragmentManager) : FragmentStatePagerAdapter(
    fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
) {
    private var removing = false

    override fun getItem(position: Int): Fragment {
        return fragments[position] as Fragment
    }

    override fun getCount(): Int {
        if (fragments == null) {
            rkUtils.toast(getActivity(MainActivity::class.java), "Error : fragment array is null")
            return 0
        }
        return fragments.size
    }

    override fun saveState(): Parcelable? {
        // Prevent saving state
        return null
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        // Do not restore state
    }

    override fun getItemPosition(`object`: Any): Int {
        if (removing) {
            return POSITION_NONE
        } else {
            val index = fragments.indexOf(`object`)
            return if (index == -1) {
                POSITION_NONE
            } else {
                index
            }
        }
    }

    fun addFragment(frag: DynamicFragment, file: File) {
        fragments.add(frag)
        notifyDataSetChanged()
        if (fragments.size > 1) StaticData.mTabLayout.getTabAt(fragments.size - 1)!!.select()
    }

    private fun onEditorRemove(fragment: DynamicFragment) {
        fileSet.remove(fragment.file)
        fragment.releaseEditor()
        if (fragments.size <= 1) {
            StaticData.menu.findItem(R.id.undo).setVisible(false)
            StaticData.menu.findItem(R.id.redo).setVisible(false)
        }
        //fragment.file?.let { FileManager.removeFileFromPreviouslyOpenedFiles(it) }
    }

    fun onNewEditor(file: File) {
        getActivity(MainActivity::class.java)?.let {
            with(it){
                binding.openBtn.visibility = View.GONE
                binding.tabs.visibility = View.VISIBLE
                binding.mainView.visibility = View.VISIBLE
                updateMenuItems()
            }
        }
        //FileManager.addFileToPreviouslyOpenedFiles(file)
    }

    fun removeFragment(position: Int) {
        val fragmentTransaction = fragmentManager.beginTransaction()

        val fragment = fragments[position]
        fragment?.let { onEditorRemove(it);fragmentTransaction.remove(it) }
        fragmentTransaction.commitNow()
        fragments.removeAt(position)

        removing = true
        notifyDataSetChanged()
        removing = false
    }

    fun closeOthers(index: Int) {
        val fragmentTransaction = fragmentManager.beginTransaction()

        val selectedObj = fragments[index]
        for (fragment in fragments) {
            if (fragment != selectedObj) {
                fragment?.let { onEditorRemove(it);fragmentTransaction.remove(it) }
            }
        }
        fragmentTransaction.commitNow()

        fragments.clear()
        fragments.add(selectedObj)

        notifyDataSetChanged()
    }

    fun clear() {
        val fragmentTransaction = fragmentManager.beginTransaction()

        for (fragment in fragments) {
            fragment?.let { onEditorRemove(it);fragmentTransaction.remove(it) }
        }
        fragmentTransaction.commitNow()

        fragments.clear()
        notifyDataSetChanged()
    }

    companion object {
        @JvmStatic
        val currentEditor: CodeEditor?
            get() = fragments[StaticData.mTabLayout.selectedTabPosition]?.editor
    }
}
