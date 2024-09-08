package com.rk.xededitor.MainActivity.tab

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.xededitor.R
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.databinding.ActivityTabBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class TabActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTabBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    val fragmentFiles = mutableListOf<File>()
    private val fragmentTitles = mutableListOf<String>()

    private val TAB_LIMIT = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SetupEditor.init(this)

        setupViewPager()
        setupTabLayout()

        if (savedInstanceState == null){
            repeat(4) {
                addFragment(File(filesDir.parentFile, "proot.sh"))
            }
        }else{
            restoreState(savedInstanceState)
        }


        setupAdapter()
    }

    private fun setupViewPager() {
        viewPager = binding.viewPager.apply {
            viewPager.offscreenPageLimit = TAB_LIMIT
            viewPager.isUserInputEnabled = false
        }
    }

    private fun setupTabLayout() {
        tabLayout = binding.tablayout.apply {
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: Tab?) {
                    viewPager.setCurrentItem(tab!!.position, false)
                }

                override fun onTabUnselected(tab: Tab?) {}
                override fun onTabReselected(tab: Tab?) {
                    val popupMenu = PopupMenu(this@TabActivity, tab!!.view)
                    popupMenu.menuInflater.inflate(R.menu.tab_menu, popupMenu.menu)
                    popupMenu.setOnMenuItemClickListener { item ->
                        val id = item.itemId
                        when (id) {
                            R.id.close_this -> {
                                removeFragment(tab.position)
                            }

                            R.id.close_others -> {
                                clearAllFragmentsExceptSelected()
                            }

                            R.id.close_all -> {
                                clearAllFragments()
                            }
                        }

                        true
                    }
                    popupMenu.show()
                }
            })
        }
    }

    private fun setupAdapter() {
        val adapter = FragmentAdapter(this, lifecycle)
        viewPager.adapter = adapter


        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = fragmentTitles[position]
        }.attach()

    }

    private fun restoreState(state: Bundle) {
        fragmentFiles.clear()
        fragmentTitles.clear()
        @Suppress("DEPRECATION")
        state.getSerializable("fileUris")?.let {
            @Suppress("UNCHECKED_CAST")
            fragmentFiles.addAll(it as List<File>)
        }
        state.getStringArrayList("titles")?.let { fragmentTitles.addAll(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("fileUris", ArrayList(fragmentFiles))
        outState.putStringArrayList("titles", ArrayList(fragmentTitles))
    }


    //adapter related
    private fun addFragment(file: File) {
        fragmentFiles.add(file)
        fragmentTitles.add(file.name)
        (viewPager.adapter as? FragmentAdapter)?.notifyItemInsertedX(fragmentFiles.size - 1)
    }

    private fun removeFragment(position: Int) {
        if (position >= 0 && position < fragmentFiles.size) {
            fragmentFiles.removeAt(position)
            fragmentTitles.removeAt(position)

            (viewPager.adapter as? FragmentAdapter)?.apply {
                notifyItemRemovedX(position)
            }

        }

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearAllFragments() {
        fragmentFiles.clear()
        fragmentTitles.clear()
        (viewPager.adapter as? FragmentAdapter)?.notifyDataSetChanged()
    }

    private fun clearAllFragmentsExceptSelected() {
        lifecycleScope.launch(Dispatchers.Main) {
            val selectedTabPosition = tabLayout.selectedTabPosition

            // Iterate backwards to avoid index shifting issues when removing fragments
            for (i in tabLayout.tabCount - 1 downTo 0) {
                if (i != selectedTabPosition) {
                    removeFragment(i)
                }
            }
        }
    }

    private var nextItemId = 0L
    inner class FragmentAdapter(activity: AppCompatActivity, lifecycle: Lifecycle) :
        FragmentStateAdapter(activity.supportFragmentManager, lifecycle) {
        private val itemIds = mutableMapOf<Int, Long>()

        override fun getItemCount(): Int = fragmentFiles.size

        override fun createFragment(position: Int): Fragment {
            val file = fragmentFiles[position]
            return TabFragment.newInstance(file)
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
        }

        fun notifyItemInsertedX(position: Int) {
            // Shift all items from the inserted position
            for (i in itemIds.size - 1 downTo position) {
                itemIds[i + 1] = itemIds[i]!!
            }
            // Add new item ID
            itemIds[position] = nextItemId++

            notifyItemInserted(position)
        }
    }
}
