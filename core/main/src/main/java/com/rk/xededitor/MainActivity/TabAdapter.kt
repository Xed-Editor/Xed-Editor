package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.rk.file_wrapper.FileObject
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.MainActivity.file.getFragmentType
import com.rk.xededitor.MainActivity.handlers.updateMenu
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.editor.getCurrentEditorFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class Kee(val file: FileObject) {
    override fun equals(other: Any?): Boolean {
        if (other !is Kee) {
            return false
        }
        return other.file.getAbsolutePath() == file.getAbsolutePath()
    }

    override fun hashCode(): Int {
        return file.getAbsolutePath().hashCode()
    }
}

private var nextItemId = 0L
const val tabLimit: Int = 20
var currentTab: WeakReference<TabLayout.Tab?> = WeakReference(null)

class TabAdapter(private val mainActivity: MainActivity) :
    FragmentStateAdapter(mainActivity.supportFragmentManager, mainActivity.lifecycle) {
    val tabFragments = HashMap<Kee, WeakReference<TabFragment>>()

    init {
        mainActivity.lifecycleScope.launch { updateMenu(getCurrentFragment()) }
    }

    // this is hell
    fun getCurrentFragment(): TabFragment? {
        if (mainActivity.tabLayout!!.selectedTabPosition == -1 || mainActivity.tabViewModel.fragmentFiles.isEmpty()) {
            tabFragments.clear()
            return null
        }

        if (currentTab.get()?.position == -1) {
            return null
        }

        currentTab.get()?.let { tab ->
            tabFragments[Kee(mainActivity.tabViewModel.fragmentFiles[tab.position])]?.get()?.let {
                return it
            }
        }
        val f = tabFragments[Kee(
            mainActivity.tabViewModel.fragmentFiles[mainActivity.tabLayout!!.selectedTabPosition]
        )]
        return f?.get()
    }

    private val itemIds = mutableMapOf<Int, Long>()

    override fun getItemCount(): Int = mainActivity.tabViewModel.fragmentFiles.size

    override fun createFragment(position: Int): Fragment {
        val file = mainActivity.tabViewModel.fragmentFiles[position]
        return TabFragment.newInstance(file).apply { tabFragments[Kee(file)] = WeakReference(this) }
    }

    override fun getItemId(position: Int): Long {
        if (!itemIds.containsKey(position)) {
            itemIds[position] = nextItemId++
        }
        return itemIds[position]!!
    }

    override fun containsItem(itemId: Long): Boolean {
        return itemIds.containsValue(itemId)
    }

    fun notifyItemRemovedX(position: Int) {
        // Shift all items after the removed position
        for (i in position until itemIds.size - 1) {
            itemIds[i] = itemIds[i + 1]!!
        }
        // Remove the last item
        itemIds.remove(itemIds.size - 1)
        notifyItemRemoved(position)
        mainActivity.lifecycleScope.launch { updateMenu(getCurrentFragment()) }
    }

    fun notifyItemInsertedX(position: Int) {
        // Shift all items from the inserted position
        for (i in itemIds.size - 1 downTo position) {
            itemIds[i + 1] = itemIds[i]!!
        }
        // Add new item ID
        itemIds[position] = nextItemId++
        notifyItemInserted(position)
        mainActivity.lifecycleScope.launch { updateMenu(getCurrentFragment()) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearAllFragments() {
        with(mainActivity) {
            tabViewModel.fileSet.clear()
            tabFragments.values.forEach { pointer -> pointer.get()?.fragment?.onClosed() }
            tabViewModel.fragmentFiles.clear()
            tabViewModel.fragmentTypes.clear()
            tabFragments.clear()
            tabViewModel.fragmentTitles.clear()
            (viewPager?.adapter as? TabAdapter)?.notifyDataSetChanged()
            binding!!.tabs.visibility = View.GONE
            binding!!.mainView.visibility = View.GONE
            binding!!.openBtn.visibility = View.VISIBLE
            DefaultScope.launch { updateMenu(mainActivity.adapter?.getCurrentFragment()) }
        }
    }

    fun removeFragment(position: Int, askUser: Boolean) {
        with(mainActivity) {
            if (position >= 0 && position < tabViewModel.fragmentFiles.size) {

                fun close() {
                    tabFragments.remove(Kee(mainActivity.tabViewModel.fragmentFiles[position]))
                    tabViewModel.fileSet.remove(tabViewModel.fragmentFiles[position].getCanonicalPath())

                    synchronized(EditorFragment.fileset) {
                        EditorFragment.fileset.remove(tabViewModel.fragmentFiles[position].getName())
                    }

                    tabViewModel.fragmentFiles.removeAt(position)
                    tabViewModel.fragmentTitles.removeAt(position)
                    tabViewModel.fragmentTypes.removeAt(position)

                    (viewPager?.adapter as? TabAdapter)?.apply { notifyItemRemovedX(position) }
                    if (tabViewModel.fragmentFiles.isEmpty()) {
                        binding?.tabs?.visibility = View.GONE
                        binding?.mainView?.visibility = View.GONE
                        binding?.openBtn?.visibility = View.VISIBLE
                    }
                }

                tabFragments[Kee(mainActivity.tabViewModel.fragmentFiles[position])]?.get()?.fragment?.let {
                    if (askUser && it is EditorFragment && it.isModified()) {
                        askClose(
                            title = strings.unsaved.getString(),
                            message = strings.ask_unsaved.getString(),
                            onCancel = {},
                            onClose = {
                                it.onClosed()
                                close()
                            })
                    } else {
                        it.onClosed()
                        close()
                    }
                }

            }
            DefaultScope.launch { updateMenu(mainActivity.adapter?.getCurrentFragment()) }
        }
    }

    fun clearAllFragmentsExceptSelected() {
        mainActivity.lifecycleScope.launch(Dispatchers.Main) {
            val selectedTabPosition = mainActivity.tabLayout?.selectedTabPosition
            var shouldAsk = false

            tabFragments.values.forEach { p ->
                p.get()?.fragment?.apply {
                    if (this is EditorFragment) {
                        if (isModified()) {
                            shouldAsk = true
                            return@forEach
                        }
                    }
                }
            }

            fun close() {
                // Iterate backwards to avoid index shifting issues when removing fragments
                for (i in mainActivity.tabLayout!!.tabCount - 1 downTo 0) {
                    if (i != selectedTabPosition) {
                        removeFragment(i, false)
                    }
                }
            }

            if (shouldAsk) {
                askClose(
                    title = strings.unsavedfiles.getString(),
                    message = strings.file_not_saved.getString(),
                    onCancel = {},
                    onClose = {
                        close()
                    })
            } else {
                close()
            }

            DefaultScope.launch { updateMenu(mainActivity.adapter?.getCurrentFragment()) }
        }
    }

    fun addFragment(file: FileObject) {
        val type = file.getFragmentType()
        if (Settings.unrestricted_files.not()) {
            if ((type == FragmentType.EDITOR) && (file.length() / (1024.0 * 1024.0)) > 10) {
                toast(strings.file_too_large)
                return
            }
        }

        with(mainActivity) {
            if (tabViewModel.fileSet.contains(file.getCanonicalPath())) {
                kotlin.runCatching {
                    MainActivity.activityRef.get()?.let {
                        if (it.tabLayout!!.selectedTabPosition == 0) {
                            return
                        }
                        it.tabLayout!!.selectTab(
                            it.tabLayout!!.getTabAt(
                                mainActivity.tabViewModel.fragmentFiles.indexOf(
                                    file
                                )
                            )
                        )

                        getCurrentEditorFragment()?.editor?.let {
                            it.requestFocus()
                            it.requestFocusFromTouch()
                        }

                    }
                }.onFailure {
                    toast(getString(strings.already_opened))
                }

                return
            }
            if (tabViewModel.fragmentFiles.size >= tabLimit) {
                toast(
                    "${getString(strings.open_cant)} $tabLimit ${getString(strings.files)}"
                )
                return
            }
            tabViewModel.fileSet.add(file.getCanonicalPath())
            tabViewModel.fragmentFiles.add(file)

            if (file.getParentFile() != null && tabViewModel.fragmentTitles.contains(file.getName())) {
                tabViewModel.fragmentTitles.add(
                    file.getParentFile()!!.getName() + "/" + file.getName()
                )
            } else {
                tabViewModel.fragmentTitles.add(file.getName())
            }

            tabViewModel.fragmentTypes.add(type)

            (viewPager?.adapter as? TabAdapter)?.notifyItemInsertedX(
                tabViewModel.fragmentFiles.size - 1
            )
            if (tabViewModel.fragmentFiles.size > 1)
                if (tabViewModel.isRestoring.not()) {
                    viewPager?.setCurrentItem(tabViewModel.fragmentFiles.size - 1, false)
                }
            binding!!.tabs.visibility = View.VISIBLE
            binding!!.mainView.visibility = View.VISIBLE
            binding!!.openBtn.visibility = View.GONE
        }
        DefaultScope.launch { updateMenu(mainActivity.adapter?.getCurrentFragment()) }
    }


    private fun askClose(
        onCancel: () -> Unit,
        onClose: () -> Unit,
        title: String,
        message: String
    ) {
        MaterialAlertDialogBuilder(mainActivity).setTitle(title).setMessage(message)
            .setNegativeButton(strings.keep_editing.getString()) { _, _ ->
                onCancel.invoke()
            }.setPositiveButton(strings.discard.getString()) { _, _ ->
                onClose.invoke()
            }.show()
    }
}
