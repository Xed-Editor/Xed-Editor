package com.rk.xededitor.MainActivity.handlers

import android.content.DialogInterface
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.model.StaticData
import com.rk.xededitor.MainActivity.model.StaticData.fragments
import com.rk.xededitor.R

class OnBackPressedHandler(private val mainActivity: MainActivity) : OnBackPressedCallback(true) {
	override fun handleOnBackPressed() {
		with(mainActivity) {
			//close drawer if opened
			if (drawerLayout!!.isDrawerOpen(GravityCompat.START)) {
				drawerLayout!!.closeDrawer(GravityCompat.START)
				return
			}
			
			fragments?.let { fragments ->
				if (!fragments.any { it.isModified }) {
					finish()
				}
				val dialog: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(this).setTitle(getString(R.string.unsaved)).setMessage(getString(R.string.unsavedfiles))
					.setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.exit)) { _: DialogInterface?, _: Int -> finish() }
				
				dialog.setNeutralButton(getString(R.string.saveexit)) { _: DialogInterface?, _: Int ->
					onOptionsItemSelected(StaticData.menu.findItem(R.id.action_all))
					finish()
				}
				dialog.show()
				
			}
		}
	}
}