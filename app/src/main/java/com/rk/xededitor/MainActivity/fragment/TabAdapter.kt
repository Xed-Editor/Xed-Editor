package com.rk.xededitor.MainActivity.fragment

import android.os.Parcelable
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.rk.xededitor.BaseActivity.Companion.getActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MainActivity.Companion.updateMenuItems
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.MainActivity.StaticData.fileSet
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

class TabAdapter(private val fragmentManager: FragmentManager) : FragmentStatePagerAdapter(
    fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
) {
    private var removing = false

    override fun getItem(position: Int): Fragment {
        return StaticData.fragments[position]
    }

    override fun getCount(): Int {
        if (StaticData.fragments == null) {
            rkUtils.toast(getActivity(MainActivity::class.java), "Error : fragment array is null")
            return 0
        }
        return StaticData.fragments.size
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
            val index = StaticData.fragments.indexOf(`object`)
            return if (index == -1) {
                POSITION_NONE
            } else {
                index
            }
        }
    }

    fun addFragment(frag: DynamicFragment?, file: File) {
        if (StaticData.fragments.contains(frag)) {
            return
        } else {
            val uri = file.path
            for (f in StaticData.fragments) {
                if (f.file!!.path == uri) {
                    return
                }
            }
        }

        StaticData.fragments.add(frag)
        notifyDataSetChanged()
        if (StaticData.fragments.size > 1) StaticData.mTabLayout.getTabAt(StaticData.fragments.size - 1)!!
            .select()
    }

    private fun onEditorRemove(fragment: DynamicFragment) {
        fileSet.remove(fragment.file)
        fragment.releaseEditor()
        if (StaticData.fragments.size <= 1) {
            StaticData.menu.findItem(R.id.undo).setVisible(false)
            StaticData.menu.findItem(R.id.redo).setVisible(false)
        }
    }

    fun onNewEditor() {
        getActivity(MainActivity::class.java)?.let {
            with(it){
                binding!!.openBtn.visibility = View.GONE
                binding!!.tabs.visibility = View.VISIBLE
                binding!!.mainView.visibility = View.VISIBLE
                updateMenuItems()
            }
        }
    }

    fun removeFragment(position: Int) {
        val fragmentTransaction = fragmentManager.beginTransaction()

        val fragment = StaticData.fragments[position]
        onEditorRemove(fragment)
        fragmentTransaction.remove(fragment)
        fragmentTransaction.commitNow()
        StaticData.fragments.removeAt(position)

        removing = true
        notifyDataSetChanged()
        removing = false
    }

    fun closeOthers(index: Int) {
        val fragmentTransaction = fragmentManager.beginTransaction()

        val selectedObj = StaticData.fragments[index]
        for (fragment in StaticData.fragments) {
            if (fragment != selectedObj) {
                onEditorRemove(fragment)
                fragmentTransaction.remove(fragment)
            }
        }
        fragmentTransaction.commitNow()

        StaticData.fragments.clear()
        StaticData.fragments.add(selectedObj)

        notifyDataSetChanged()
    }

    fun clear() {
        val fragmentTransaction = fragmentManager.beginTransaction()

        for (fragment in StaticData.fragments) {
            onEditorRemove(fragment)
            fragmentTransaction.remove(fragment)
        }
        fragmentTransaction.commitNow()

        StaticData.fragments.clear()
        notifyDataSetChanged()
    }

    companion object {
        @JvmStatic
        val currentEditor: CodeEditor
            get() = StaticData.fragments[StaticData.mTabLayout.selectedTabPosition].editor
    }
}
