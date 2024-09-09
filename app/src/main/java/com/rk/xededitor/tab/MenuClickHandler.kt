package com.rk.xededitor.tab

import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.Printer
import com.rk.xededitor.MainActivity.BatchReplacement.BatchReplacement
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsMainActivity
import com.rk.xededitor.rkUtils
import com.rk.xededitor.terminal.Terminal

object MenuClickHandler {


    private var searchText: String? = ""

    fun handle(activity: TabActivity, menuItem: MenuItem): Boolean {
        val id = menuItem.itemId
        when (id) {

            R.id.run -> {
                //todo get current fragmnets file
                return true
            }

            R.id.action_all -> {
                activity.tabFragments.forEach { f ->
                    f.get()?.save()
                }
                return true
            }

            R.id.action_save -> {
                activity.getCurrentFragment()?.get()?.save()
                return true
            }

            R.id.undo -> {
                activity.getCurrentFragment()?.get()?.undo()
                updateUndoRedoMenuItems()
                return true
            }

            R.id.redo -> {
                activity.getCurrentFragment()?.get()?.redo()
                updateUndoRedoMenuItems()
                return true
            }

            R.id.action_settings -> {
                activity.startActivity(Intent(activity, SettingsMainActivity::class.java))
                return true
            }

            R.id.terminal -> {
                // Handle terminal
                activity.startActivity(Intent(activity, Terminal::class.java))
                return true
            }

            R.id.action_print -> {
                Printer.print(
                    activity,
                    activity.getCurrentFragment()?.get()?.editor?.text.toString()
                )
                return true
            }

            R.id.batchrep -> {
                // Handle batchrep
                activity.startActivity(Intent(activity, BatchReplacement::class.java))
                return true
            }

            R.id.search -> {
                // Handle search
                handleSearch(activity)
                return true
            }

            R.id.search_next -> {
                // Handle search_next
                handleSearchNext()
                return true
            }

            R.id.search_previous -> {
                // Handle search_previous
                handleSearchPrevious()
                return true
            }

            R.id.search_close -> {
                // Handle search_close
                handleSearchClose()
                return true
            }

            R.id.replace -> {
                // Handle replace
                handleReplace(activity)
                return true
            }

            R.id.share -> {
                rkUtils.shareText(
                    activity,
                    activity.getCurrentFragment()?.get()?.editor?.text.toString()
                )
                return true
            }

            else -> return false
        }


    }


    private fun updateUndoRedoMenuItems() {

    }

    private fun handleReplace(activity: TabActivity): Boolean {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_replace, null)
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.replace))
            .setView(popupView).setNegativeButton(activity.getString(R.string.cancel), null)
            .setPositiveButton("replace All") { _, _ ->
                replaceAll(popupView)
            }.show()
        return true
    }

    private fun replaceAll(popupView: View) {

    }


    private fun handleSearchNext(): Boolean {
        return true
    }

    private fun handleSearchPrevious(): Boolean {
        return true
    }

    private fun handleSearchClose(): Boolean {
        hideSearchMenuItems()
        searchText = ""
        return true
    }

    private fun handleSearch(activity: TabActivity): Boolean {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_search, null)
        val searchBox = popupView.findViewById<EditText>(R.id.searchbox)

        if (!searchText.isNullOrEmpty()) {
            searchBox.setText(searchText)
        }

        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.search))
            .setView(popupView).setNegativeButton(activity.getString(R.string.cancel), null)
            .setPositiveButton(activity.getString(R.string.search)) { _, _ ->
                //search
                initiateSearch(searchBox, popupView)
            }.show()
        return true
    }

    private fun initiateSearch(searchBox: EditText, popupView: View) {
        searchText = searchBox.text.toString()

        if (searchText?.isBlank() == true) {
            return
        }
        showSearchMenuItems()
    }


    fun showSearchMenuItems() {
//		with(StaticData.menu){
//			findItem(R.id.search_next).isVisible = true
//			findItem(R.id.search_previous).isVisible = true
//			findItem(R.id.search_close).isVisible = true
//			findItem(R.id.replace).isVisible = true
//			findItem(R.id.undo).isVisible = false
//			findItem(R.id.redo).isVisible = false
//			findItem(R.id.run).isVisible = false
//		}
//
    }

    fun hideSearchMenuItems() {
//		with(StaticData.menu){
//			findItem(R.id.search_next).isVisible = false
//			findItem(R.id.search_previous).isVisible = false
//			findItem(R.id.search_close).isVisible = false
//			findItem(R.id.replace).isVisible = false
//
//			val v = !(mTabLayout.selectedTabPosition == -1 && fragments.isNullOrEmpty())
//			findItem(R.id.run).isVisible = v && Runner.isRunnable(fragments[mTabLayout.selectedTabPosition].file!!)
//
//			if (mTabLayout.selectedTabPosition != -1) {
//				findItem(R.id.undo).isVisible = true
//				findItem(R.id.redo).isVisible = true
//			}
//		}


    }

}
